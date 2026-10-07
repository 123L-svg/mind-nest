<template>
  <div class="editor-page">
    <el-header class="bar">
      <el-button :icon="ArrowLeft" @click="back">返回</el-button>
      <div class="edit-title">{{ isEdit ? '编辑笔记' : '新建笔记' }}</div>
      <template v-if="isEdit">
        <el-button @click="exportMarkdown">导出 MD</el-button>
        <el-button @click="exportPdf">导出 PDF</el-button>
        <el-button @click="openVersions">历史</el-button>
      </template>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      <el-tag v-if="autoSaveHint" size="small" type="info">{{ autoSaveHint }}</el-tag>
    </el-header>

    <el-main class="body">
      <el-row class="fill-row">
        <el-col :xs="24" :lg="18">
          <el-card class="main-card">
            <el-form label-position="top">
              <el-form-item class="title-item">
                <el-input v-model="form.title" class="title-input" placeholder="输入笔记标题…" clearable />
              </el-form-item>
              <el-form-item>
                <div class="meta">
                  <el-select v-model="form.kbId" placeholder="选择知识库" @change="loadCategories">
                    <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
                  </el-select>
                  <el-select v-model="form.categoryId" placeholder="选择分类（可选）" clearable>
                    <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
                  </el-select>
                </div>
              </el-form-item>
              <el-form-item label="内容">
                <div class="editor-box">
                  <Toolbar class="editor-toolbar" :editor="editorRef" :defaultConfig="toolbarConfig" mode="default" />
                  <Editor class="editor-body" v-model="html" :defaultConfig="editorConfig" mode="default"
                          @onCreated="handleCreated" @onChange="handleChange" />
                </div>
              </el-form-item>
              <el-form-item label="摘要">
                <el-input v-model="form.summary" placeholder="笔记摘要（可选）" clearable />
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="6">
          <el-card class="side">
            <template #header>
              <div class="side-header">
                <span class="side-dot" aria-hidden="true"></span>
                <b>AI 助手</b>
                <el-tag v-if="ai.mock" type="warning" size="small">演示模式</el-tag>
                <el-button v-if="chatMsgs.length" link size="small" class="chat-clear"
                           @click="clearChat">清空</el-button>
              </div>
            </template>

            <!-- 对话消息区 -->
            <div v-if="chatMsgs.length" ref="chatListRef" class="chat-list">
              <div v-for="(m, i) in chatMsgs" :key="i" class="chat-msg" :class="m.role">
                <div class="chat-bubble" :class="{ error: m.error }">
                  <pre>{{ m.pending ? 'AI 处理中…' : m.text }}</pre>
                  <el-button v-if="m.role === 'ai' && m.action === 'outline' && !m.pending"
                             type="primary" size="small" class="insert-btn"
                             @click="insertOutline(m.text)">插入正文</el-button>
                </div>
              </div>
            </div>
            <!-- 空状态 -->
            <div v-else class="chat-empty">
              <div class="chat-empty-icon"><el-icon :size="20"><ChatDotRound /></el-icon></div>
              <p>我是你的 AI 写作助手</p>
              <p class="sub">像我一样直接对话，或点击下方快捷功能处理笔记</p>
            </div>

            <!-- 异步任务（MQ）折叠区 -->
            <el-collapse class="async-collapse">
              <el-collapse-item title="异步任务（MQ）" name="mq">
                <p class="side-hint">入队后由消费者异步生成，前端轮询结果</p>
                <div class="ai-async">
                  <el-select v-model="asyncForm.action" placeholder="选择动作">
                    <el-option label="生成大纲" value="outline" />
                    <el-option label="润色内容" value="polish" />
                    <el-option label="生成摘要" value="summarize" />
                    <el-option label="内容问答" value="chat" />
                  </el-select>
                  <el-button type="primary" :loading="asyncBusy"
                             :disabled="asyncForm.action !== 'chat' && !form.content" @click="submitAsync">
                    提交异步任务
                  </el-button>
                  <div v-if="asyncTask.status != null" class="async-state">
                    <span>状态：</span>
                    <el-tag :type="asyncStatusTag(asyncTask.status)">{{ asyncTask.statusText }}</el-tag>
                  </div>
                  <el-alert v-if="asyncTask.errorMsg" type="error" :closable="false"
                            :title="asyncTask.errorMsg" />
                  <div v-if="asyncTask.result" class="ai-result">
                    <pre>{{ asyncTask.result }}</pre>
                  </div>
                </div>
              </el-collapse-item>
            </el-collapse>

            <!-- 快捷功能工具条 -->
            <div class="chat-tools">
              <button class="chat-tool" :disabled="aiBusy || !form.content" @click="genOutline">
                <el-icon><List /></el-icon>大纲
              </button>
              <button class="chat-tool" :disabled="aiBusy || !form.content" @click="polish">
                <el-icon><MagicStick /></el-icon>润色
              </button>
              <button class="chat-tool" :disabled="aiBusy || !form.content" @click="summarize">
                <el-icon><Document /></el-icon>摘要
              </button>
            </div>

            <!-- 输入区 -->
            <div class="chat-input">
              <el-input v-model="chatQuestion" type="textarea" :rows="2" resize="none"
                        placeholder="随便聊点什么，Enter 发送，Shift+Enter 换行"
                        @keydown.enter.exact.prevent="askChat" />
              <el-button type="primary" class="chat-send" :icon="Promotion"
                         :loading="aiBusy" :disabled="!chatQuestion" @click="askChat" />
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-main>

    <!-- 历史版本弹窗 -->
    <el-dialog v-model="verDialog.show" title="历史版本" width="760px" top="6vh">
      <el-table :data="verDialog.list" empty-text="暂无历史版本">
        <el-table-column prop="createTime" label="时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="140" />
        <el-table-column prop="summary" label="摘要" min-width="180" />
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewVersion(row.id)">查看</el-button>
            <el-button link type="success" @click="doRollback(row.id)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="verDialog.current" class="ver-current">
        <h4>{{ verDialog.current.title }}</h4>
        <div class="rich" v-html="verDialog.current.content || '<p>（空）</p>'"></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, shallowRef, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import { List, MagicStick, Document, ArrowLeft, ChatDotRound, Promotion } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { kbApi, categoryApi, noteApi, aiApi, fileApi, exportApi } from '@/api'
import '@wangeditor/editor/dist/css/style.css'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.query.id)

const kbs = ref([])
const categories = ref([])
const aiBusy = ref(false)
const chatQuestion = ref('')
const ai = ref({ action: '', mock: false, result: '' })
const saving = ref(false)

// 历史版本 / 自动保存
const verDialog = ref({ show: false, list: [], current: null })
const autoSaveHint = ref('')
let autoSaveTimer = null
let lastSaved = ''

function scheduleAutoSave() {
  clearTimeout(autoSaveTimer)
  const changed = form.value.content !== lastSaved
  autoSaveTimer = setTimeout(() => {
    if (!isEdit.value || !form.value.id || !changed) return
    autoSave().catch(() => {})
  }, 5000)
}

async function autoSave() {
  if (form.value.id && form.value.content !== lastSaved) {
    await noteApi.update({
      id: form.value.id, kbId: form.value.kbId || null,
      categoryId: form.value.categoryId || null,
      title: form.value.title, content: form.value.content, summary: form.value.summary
    })
    lastSaved = form.value.content
    autoSaveHint.value = '已自动保存 ' + new Date().toLocaleTimeString()
    clearTimeout(autoSaveTimer)
  }
}

async function openVersions() {
  if (!form.value.id) return
  const res = await noteApi.versions(form.value.id)
  verDialog.value = { show: true, list: res.data, current: null }
}

async function viewVersion(id) {
  verDialog.value.current = (await noteApi.versionDetail(id)).data
}

async function doRollback(id) {
  try {
    await window.$confirm('回滚到该版本？当前内容将被覆盖，历史仍保留。', '提示', { type: 'warning' })
  } catch { return }
  await noteApi.rollback(id)
  window.$message?.success('已回滚')
  verDialog.value = { show: false, list: [], current: null }
  await loadNote()
}

const editorRef = shallowRef()
const html = ref('')
const editorConfig = {
  placeholder: '在此输入笔记内容...',
  uploadImage: {
    maxFileSize: 10 * 1024 * 1024,
    async customUpload(file, insertFn) {
      const res = await fileApi.upload(file)
      const url = '/api' + res.data.path
      insertFn(url, res.data.originalName || '', url)
    }
  }
}
const toolbarConfig = { excludeKeys: ['group-video', 'uploadVideo'] }

function handleCreated(editor) { editorRef.value = editor }
function handleChange() {
  if (editorRef.value) {
    form.value.content = editorRef.value.getHtml()
    scheduleAutoSave()
  }
}

const form = ref({ id: null, kbId: '', categoryId: '', title: '', content: '', summary: '' })

function plainText() {
  if (editorRef.value) return editorRef.value.getText()
  return form.value.content || ''
}

async function loadKbs() {
  kbs.value = (await kbApi.list()).data
  if (isEdit.value && form.value.kbId) await loadCategories()
}
async function loadCategories() {
  if (!form.value.kbId) return
  try { categories.value = (await categoryApi.list(form.value.kbId)).data } catch { categories.value = [] }
}
async function loadNote() {
  const d = (await noteApi.detail(route.query.id)).data
  form.value = { id: d.id, kbId: d.kbId, categoryId: d.categoryId || '', title: d.title, content: d.content || '', summary: d.summary || '' }
  html.value = form.value.content || '<p><br></p>'
  lastSaved = form.value.content
}
function back() { router.push('/dashboard') }

async function save() {
  if (!form.value.title) return window.$message?.warning('标题不能为空')
  saving.value = true
  try {
    const payload = { ...form.value, kbId: form.value.kbId || null, categoryId: form.value.categoryId || null }
    let noteId = form.value.id
    if (isEdit.value) { await noteApi.update(payload) } else {
      const res = await noteApi.add(payload); noteId = res.data; form.value.id = noteId
    }
    await bindFilesToNote(noteId)
    window.$message?.success('保存成功')
    await back()
  } catch (e) { window.$message?.error('保存失败：' + e.message) } finally { saving.value = false }
}

async function bindFilesToNote(noteId) {
  try {
    const files = (await fileApi.list()).data || []
    const content = form.value.content || ''
    const toBind = files.filter((f) => !f.noteId && content.includes(f.path))
    await Promise.all(toBind.map((f) => fileApi.bind(f.id, noteId)))
  } catch (e) { console.warn('文件绑定到笔记失败', e.message) }
}

/** 简易 Markdown -> HTML（覆盖 AI 大纲常用标题/列表） */
function markdownToHtml(md) {
  let html = ''
  let inList = false
  const closeList = () => { if (inList) { html += '</ul>'; inList = false } }
  for (const raw of (md || '').split('\n')) {
    const t = raw.trim()
    const h = t.match(/^#{1,6}\s+(.*)$/)
    if (h) {
      closeList()
      html += `<h${h[1].length}>${h[2]}</h${h[1].length}>`
    } else if (/^[-*+]\s+/.test(t)) {
      if (!inList) { html += '<ul>'; inList = true }
      html += `<li>${t.replace(/^[-*+]\s+/, '')}</li>`
    } else if (t) {
      closeList()
      html += `<p>${t}</p>`
    } else {
      closeList()
    }
  }
  closeList()
  return html
}

/** AI 大纲一键插入正文（wangEditor 光标处） */
function insertOutline(text) {
  if (!editorRef.value || !text) return
  const html = markdownToHtml(text)
  editorRef.value.focus()
  editorRef.value.dangerouslyInsertHtml(html)
  window.$message?.success('大纲已插入正文，可继续编辑后保存')
}

// ============ AI 对话 ============
const chatMsgs = ref([])          // {role:'user'|'ai', text, action, pending, error}
const chatListRef = ref(null)
const ACTION_LABELS = { outline: '生成大纲', polish: '润色内容', summarize: '生成摘要', chat: '内容问答' }

// ============ 对话记忆（Redis 多轮，按笔记隔离） ============
async function loadChatHistory() {
  try {
    const { data } = await aiApi.chatHistory(form.value.id)
    chatMsgs.value = (data || []).map(m => ({
      role: m.role === 'assistant' ? 'ai' : 'user',
      text: m.content
    }))
    if (chatMsgs.value.length) nextTick(scrollChatToBottom)
  } catch { /* 历史加载失败不阻塞编辑 */ }
}

async function clearChat() {
  try {
    await ElMessageBox.confirm('确定清空当前笔记的对话记录吗？', '清空对话', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch { return }  // 用户取消
  await aiApi.clearChatHistory(form.value.id)
  chatMsgs.value = []
  ElMessage.success('对话已清空')
}

function scrollChatToBottom() {
  nextTick(() => { chatListRef.value?.scrollTo({ top: chatListRef.value.scrollHeight }) })
}

/** 统一 AI 动作：提交异步 MQ + 轮询，结果以对话消息气泡呈现 */
async function submitAiAsync(action) {
  if (action !== 'chat' && !form.value.content) return
  if (action === 'chat' && !chatQuestion.value) return
  const question = action === 'chat' ? chatQuestion.value : null
  if (action === 'chat') chatQuestion.value = ''
  aiBusy.value = true
  ai.value = { ...ai.value, result: '', action }
  chatMsgs.value.push({ role: 'user', text: action === 'chat' ? question : '✦ ' + ACTION_LABELS[action] })
  const idx = chatMsgs.value.push({ role: 'ai', text: '', pending: true }) - 1
  scrollChatToBottom()
  try {
    const res = await aiApi.asyncSubmit({
      action,
      noteId: form.value.id,
      title: form.value.title,
      content: plainText(),
      question
    })
    const task = await pollTask(res.data.taskId)
    if (task && task.status === 3) {
      chatMsgs.value[idx] = { role: 'ai', text: task.errorMsg || 'AI 任务处理失败', error: true }
    } else if (task) {
      chatMsgs.value[idx] = { role: 'ai', text: task.result, action }
      if (action === 'summarize') form.value.summary = task.result
    }
  } catch (e) {
    chatMsgs.value[idx] = { role: 'ai', text: e.message, error: true }
  } finally {
    aiBusy.value = false
    scrollChatToBottom()
  }
}

async function exportMarkdown() {
  if (!form.value.id) return window.$message?.warning('请先保存笔记')
  try {
    const res = await exportApi.markdown(form.value.id)
    const blob = new Blob([res.data], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob); const a = document.createElement('a')
    a.href = url; a.download = `${form.value.title || 'note'}.md`; a.click(); URL.revokeObjectURL(url)
  } catch (e) { window.$message?.error('导出失败：' + e.message) }
}

async function exportPdf() {
  if (!form.value.id) return window.$message?.warning('请先保存笔记')
  try {
    const res = await exportApi.markdown(form.value.id)
    const md = res.data
      .replace(/^#{1,6}\s+(.*$)/gm, (m, c) => `<h6>${c.trim()}</h6>`)
      .replace(/^>\s?(.*)$/gm, `<blockquote>$1</blockquote>`)
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      .replace(/^-\s?(.*)$/gm, '<li>$1</li>')
      .replace(/\n/g, '<br/>')
    const t = form.value.title || 'note'
    const htmlDoc = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${t}</title></head><body><h1>${t}</h1><div>${md}</div><script>window.onload=function(){window.print()}<\/script></body></html>`
    const iframe = document.createElement('iframe')
    iframe.setAttribute('style', 'position:fixed;width:0;height:0;border:0;visibility:hidden')
    document.body.appendChild(iframe)
    const idoc = iframe.contentWindow.document
    idoc.open(); idoc.write(htmlDoc); idoc.close()
    iframe.contentWindow.onafterprint = () => iframe.remove()
    setTimeout(() => { if (iframe.isConnected) iframe.remove() }, 60000)
  } catch (e) { window.$message?.error('导出失败：' + e.message) }
}

/** 快捷入口：均走对话式异步 MQ 流程 */
function genOutline() { submitAiAsync('outline') }
function polish() { submitAiAsync('polish') }
function summarize() { submitAiAsync('summarize') }
function askChat() { submitAiAsync('chat') }

// ============ 异步任务（MQ 解耦 + 轮询） ============
const asyncForm = ref({ action: 'outline' })
const asyncTask = ref({})           // {taskId,status,statusText,result,errorMsg}
const asyncBusy = ref(false)
const TERMINAL = [2, 3]
const unmounted = ref(false)
let pollTimers = new Set()

/** 通用轮询：直到终态（2成功/3失败）或组件卸载。卸载时 resolve(null) */
function pollTask(taskId) {
  return new Promise((resolve) => {
    const rec = { id: null }
    pollTimers.add(rec)
    const run = async () => {
      if (unmounted.value) { pollTimers.delete(rec); return resolve(null) }
      let t = {}
      try { t = (await aiApi.asyncTask(taskId)).data } catch { /* 瞬时错误忽略，继续轮询 */ }
      if (TERMINAL.includes(t.status)) { pollTimers.delete(rec); return resolve(t) }
      rec.id = setTimeout(run, 1500)
    }
    run()
  })
}

function asyncStatusTag(status) {
  if (status === 2) return 'success'
  if (status === 3) return 'danger'
  if (status === 0) return 'info'
  return 'warning'
}

function asyncPayload() {
  return {
    action: asyncForm.value.action,
    title: form.value.title,
    content: plainText(),
    question: chatQuestion.value || (asyncForm.value.action === 'chat' ? '' : null)
  }
}

async function submitAsync() {
  asyncBusy.value = true
  asyncTask.value = {}
  try {
    const res = await aiApi.asyncSubmit(asyncPayload())
    const task = await pollTask(res.data.taskId)
    if (task) {
      asyncTask.value = task
      if (task.status === 3) window.$message?.error(task.errorMsg || '任务处理失败')
      else window.$message?.success('异步任务已完成')
    }
  } catch (e) {
    window.$message?.error(e.message)
  } finally {
    asyncBusy.value = false
  }
}

onBeforeUnmount(() => {
  unmounted.value = true
  clearTimeout(autoSaveTimer)
  pollTimers.forEach((rec) => clearTimeout(rec.id))
  pollTimers.clear()
  editorRef.value?.destroy()
})
onMounted(async () => {
  await loadKbs()
  if (route.query.kbId) { form.value.kbId = route.query.kbId; await loadCategories() }
  if (isEdit.value) await loadNote()
  loadChatHistory()
})
</script>

<style scoped>
/* 全屏工作台：顶栏 + 左右两栏撑满视口，各自内部滚动 */
.editor-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 顶栏 */
.bar {
  flex-shrink: 0;
  height: 56px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 24px;
  border-bottom: 1px solid var(--c-border);
  background: var(--c-surface);
}
.edit-title { flex: 1; font-size: 16px; font-weight: 600; }

.body { flex: 1; min-height: 0; padding: 0; background: var(--c-bg); overflow: hidden; }
.fill-row { height: 100%; }
.fill-row :deep(.el-col) { height: 100%; }

/* 左栏：编辑器卡片铺满，右缘分隔线 */
.main-card {
  height: 100%;
  margin-bottom: 0;
  border: none;
  border-right: 1px solid var(--c-border);
  border-radius: 0;
  box-shadow: none;
  display: flex;
  flex-direction: column;
}
.main-card :deep(.el-card__body) { flex: 1; overflow-y: auto; padding: 28px 36px; }
.main-card :deep(.el-form-item__label) { font-weight: 600; padding-bottom: 6px; }
.main-card :deep(.el-form-item:last-child) { margin-bottom: 0; }

/* 标题：Notion 风格大号无边框输入 */
.title-item { margin-bottom: 10px; }
.title-input :deep(.el-input__wrapper) {
  box-shadow: none;
  padding: 4px 0;
  background: transparent;
}
.title-input :deep(.el-input__wrapper:hover),
.title-input :deep(.el-input__wrapper.is-focus) { box-shadow: none; }
.title-input :deep(.el-input__inner) {
  font-size: 22px;
  font-weight: 700;
  height: 38px;
  color: var(--c-text);
}
.title-input :deep(.el-input__inner::placeholder) {
  color: var(--c-text-sub);
  font-weight: 500;
  opacity: 0.55;
}

/* 知识库/分类自适应填满整行，贴合容器 */
.meta { display: flex; gap: 12px; width: 100%; }
.meta :deep(.el-select) { flex: 1; }

/* 编辑器：固定可视高度，内部滚动，避免下方大片留白 */
.editor-box {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  overflow: hidden;
  width: 100%;
  height: clamp(360px, 52vh, 560px);
}
.editor-toolbar {
  border-bottom: 1px solid var(--c-border);
  background: var(--c-surface-sub);
}
.editor-body { flex: 1; min-height: 0; }
.editor-body :deep(.w-e-text-container) { height: 100%; }

/* AI 面板：铺满右栏，对话式布局 */
.side {
  height: 100%;
  border: none;
  border-radius: 0;
  box-shadow: none;
  display: flex;
  flex-direction: column;
  background: var(--c-surface);
}
.side :deep(.el-card__header) { flex-shrink: 0; padding: 14px 16px; }
.side :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.side-header { display: flex; align-items: center; gap: 8px; }
.side-header b { font-size: 15px; }
.chat-clear { margin-left: auto; }
.side-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--grad-brand);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.15);
}
.side-hint { font-size: 12px; color: var(--c-text-sub); margin: 4px 0 10px; line-height: 1.5; }

/* 对话消息区 */
.chat-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 14px 6px;
}
.chat-msg { display: flex; margin-bottom: 10px; }
.chat-msg.user { justify-content: flex-end; }
.chat-msg.ai { justify-content: flex-start; }
.chat-bubble {
  max-width: 86%;
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
}
.chat-msg.user .chat-bubble {
  background: var(--c-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.chat-msg.ai .chat-bubble {
  background: var(--c-surface-sub);
  border-bottom-left-radius: 4px;
}
.chat-bubble.error { color: var(--c-danger); }
.chat-bubble pre { white-space: pre-wrap; word-break: break-word; font-family: inherit; margin: 0; }

/* 空状态 */
.chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--c-text-sub);
  padding: 20px;
}
.chat-empty-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--grad-brand-soft);
  color: var(--c-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 6px;
}
.chat-empty p { font-size: 13px; font-weight: 500; color: var(--c-text); }
.chat-empty .sub { font-size: 12px; }

/* 异步任务折叠区 */
.async-collapse { border-top: 1px solid var(--c-divider); flex-shrink: 0; }
.async-collapse :deep(.el-collapse-item__header) {
  height: 40px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 600;
  background: transparent;
  border-bottom: none;
}
.async-collapse :deep(.el-collapse-item__wrap) { border-bottom: none; background: transparent; }
.async-collapse :deep(.el-collapse-item__content) { padding: 0 14px 12px; }

/* 快捷功能工具条 */
.chat-tools {
  display: flex;
  gap: 6px;
  padding: 8px 12px 0;
  flex-shrink: 0;
}
.chat-tool {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 0;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: 999px;
  font-size: 12px;
  font-family: inherit;
  color: var(--c-text);
  cursor: pointer;
  transition: border-color var(--t-fast), color var(--t-fast), background var(--t-fast);
}
.chat-tool:hover:not(:disabled) {
  border-color: var(--c-primary);
  color: var(--c-primary);
  background: var(--grad-brand-soft);
}
.chat-tool:disabled { opacity: 0.5; cursor: not-allowed; }
.chat-tool .el-icon { font-size: 13px; }

/* 输入区 */
.chat-input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 10px 12px 12px;
  flex-shrink: 0;
}
.chat-input :deep(.el-textarea__inner) { border-radius: 10px; }
.chat-send {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  padding: 0;
}

/* 侧栏窄面板：按钮单列纵排，避免 2+1 参差换行 */
.ai-result { margin-top: 12px; background: var(--c-surface-sub); border-radius: var(--r-md); padding: 10px; max-height: 40vh; overflow: auto; }
.ai-result pre { white-space: pre-wrap; word-break: break-word; font-size: 13px; }
.ai-async { display: flex; flex-direction: column; gap: 10px; }
.async-state { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.insert-btn { margin-top: 10px; width: 100%; }

/* 中窄屏：回退为常规滚动文档流（上下堆叠） */
@media (max-width: 1199.98px) {
  .editor-page { height: auto; min-height: 100vh; overflow: visible; }
  .bar { position: sticky; top: 0; z-index: 20; }
  .body { overflow: visible; padding: 12px; }
  .fill-row :deep(.el-col) { height: auto; }
  .main-card { height: auto; border-right: none; border-radius: var(--r-lg); margin-bottom: 12px; }
  /* 对话面板给定高度，保证消息区可滚动 */
  .side { height: 72vh; border-radius: var(--r-lg); }
}
</style>