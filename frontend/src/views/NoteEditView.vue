<template>
  <div class="editor-page page-shell">
    <PageHeader :title="isEdit ? '编辑笔记' : '新建笔记'"
                subtitle="富文本编辑 · 自动保存 · AI 辅助创作">
      <el-tag v-if="autoSaveHint" size="small" type="info" effect="plain" class="autosave-tag">
        {{ autoSaveHint }}
      </el-tag>
      <template v-if="isEdit">
        <el-button size="small" @click="exportMarkdown">导出 MD</el-button>
        <el-button size="small" @click="exportPdf">导出 PDF</el-button>
        <el-button size="small" @click="openVersions">历史版本</el-button>
      </template>
      <el-button type="primary" size="small" :loading="saving" @click="save">保存</el-button>
    </PageHeader>

    <main class="page-container">
      <el-row :gutter="16">
        <el-col :xs="24" :lg="17">
          <el-card class="main-card">
            <el-form label-position="top">
              <el-form-item label="笔记标题">
                <el-input v-model="form.title" placeholder="给笔记起个标题…" clearable
                          class="title-input" />
              </el-form-item>
              <el-form-item>
                <div class="meta">
                  <el-select v-model="form.kbId" placeholder="选择知识库" class="meta-select"
                             @change="loadCategories">
                    <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
                  </el-select>
                  <el-select v-model="form.categoryId" placeholder="选择分类（可选）" clearable
                             class="meta-select">
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
                <el-input v-model="form.summary" placeholder="笔记摘要（可选，AI 生成摘要后自动填入）" clearable />
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="7">
          <el-card class="side">
            <template #header>
              <div class="side-head">
                <span class="side-title"><el-icon><MagicStick /></el-icon>AI 助手</span>
                <el-tag v-if="ai.mock" type="warning" size="small">演示模式</el-tag>
              </div>
            </template>

            <el-tabs v-model="aiTab" class="side-tabs">
              <!-- 快捷工具：三个常用 AI 动作 + 内容问答（均走异步 MQ + 轮询） -->
              <el-tab-pane name="quick">
                <template #label>
                  <span class="tab-label"><el-icon><Lightning /></el-icon>快捷工具</span>
                </template>
                <div class="ai-btns">
                  <el-button class="ai-quick" :loading="aiBusy" @click="genOutline">
                    <el-icon v-show="!aiBusy"><EditPen /></el-icon><span>生成大纲</span>
                  </el-button>
                  <el-button class="ai-quick" :loading="aiBusy" :disabled="!form.content" @click="polish">
                    <el-icon v-show="!aiBusy"><MagicStick /></el-icon><span>润色内容</span>
                  </el-button>
                  <el-button class="ai-quick" :loading="aiBusy" :disabled="!form.content" @click="summarize">
                    <el-icon v-show="!aiBusy"><Tickets /></el-icon><span>生成摘要</span>
                  </el-button>
                </div>
                <div class="chat-row">
                  <el-input v-model="chatQuestion" placeholder="基于内容提问，回车发送"
                            clearable @keyup.enter="askChat" :disabled="!form.content" />
                  <el-button class="chat-send" :icon="Promotion" type="primary" :loading="aiBusy"
                             :disabled="!form.content || !chatQuestion" @click="askChat" />
                </div>
                <el-alert v-if="aiBusy" title="AI 处理中，请稍候…" type="info" :closable="false"
                          class="ai-busy-alert" />
                <div v-if="ai.result && !aiBusy" class="ai-result">
                  <pre>{{ ai.result }}</pre>
                  <el-button v-if="ai.action === 'outline'" type="primary" size="small"
                             class="insert-btn" @click="insertOutline">插入正文</el-button>
                </div>
              </el-tab-pane>

              <!-- 任务中心：显式选择动作提交异步任务，查看状态与结果 -->
              <el-tab-pane name="task">
                <template #label>
                  <span class="tab-label"><el-icon><List /></el-icon>任务中心</span>
                </template>
                <p class="mq-hint">任务入队后由消费者异步生成，前端轮询返回结果。</p>
                <div class="ai-async">
                  <el-select v-model="asyncForm.action" class="async-select" placeholder="选择动作">
                    <el-option label="生成大纲" value="outline" />
                    <el-option label="润色内容" value="polish" />
                    <el-option label="生成摘要" value="summarize" />
                    <el-option label="内容问答" value="chat" />
                  </el-select>
                  <el-input v-if="asyncForm.action === 'chat'" v-model="asyncQuestion"
                            class="async-question" placeholder="请输入要提问的问题" clearable />
                  <el-button type="primary" class="async-submit" :loading="asyncBusy"
                             :disabled="(asyncForm.action !== 'chat' && !form.content)
                                        || (asyncForm.action === 'chat' && !asyncQuestion)"
                             @click="submitAsync">
                    提交异步任务
                  </el-button>
                  <div v-if="asyncTask.status != null" class="async-state">
                    <span class="muted">状态：</span>
                    <el-tag :type="asyncStatusTag(asyncTask.status)" size="small">
                      {{ asyncTask.statusText }}
                    </el-tag>
                  </div>
                  <el-alert v-if="asyncTask.errorMsg" type="error" :closable="false"
                            :title="asyncTask.errorMsg" class="async-error" />
                  <div v-if="asyncTask.result" class="ai-result">
                    <pre>{{ asyncTask.result }}</pre>
                  </div>
                </div>
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </el-col>
      </el-row>
    </main>

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
import { ref, shallowRef, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import {
  EditPen, MagicStick, Tickets, Promotion, List, Lightning
} from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { kbApi, categoryApi, noteApi, aiApi, fileApi, exportApi } from '@/api'
import '@wangeditor/editor/dist/css/style.css'

// AI 面板当前 Tab：quick 快捷工具 / task 任务中心
const aiTab = ref('quick')

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
function insertOutline() {
  if (!editorRef.value || !ai.value.result) return
  const html = markdownToHtml(ai.value.result)
  editorRef.value.focus()
  editorRef.value.dangerouslyInsertHtml(html)
  window.$message?.success('大纲已插入正文，可继续编辑后保存')
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

/** 同步 AI 按钮统一改造：提交异步 MQ + 轮询，解决真实模型推理可能超时的问题 */
async function submitAiAsync(action) {
  if (action !== 'chat' && !form.content) return
  if (action === 'chat' && !chatQuestion.value) return
  aiBusy.value = true
  ai.value = { ...ai.value, result: '', action }
  try {
    const res = await aiApi.asyncSubmit({
      action,
      title: form.value.title,
      content: plainText(),
      question: action === 'chat' ? chatQuestion.value : null
    })
    const task = await pollTask(res.data.taskId)
    if (task && task.status === 3) {
      window.$message?.error(task.errorMsg || 'AI 任务处理失败')
    } else if (task) {
      window.$message?.success('AI 处理完成')
      ai.value.result = task.result
      if (action === 'summarize') form.value.summary = task.result
    }
  } catch (e) {
    window.$message?.error(e.message)
  } finally {
    aiBusy.value = false
  }
}
function genOutline() { submitAiAsync('outline') }
function polish() { submitAiAsync('polish') }
function summarize() { submitAiAsync('summarize') }
function askChat() { submitAiAsync('chat') }

// ============ 异步任务（MQ 解耦 + 轮询） ============
const asyncForm = ref({ action: 'outline' })
const asyncQuestion = ref('')
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
    question: asyncForm.value.action === 'chat' ? asyncQuestion.value : null
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
})
</script>

<style scoped>
.editor-page { min-height: 100vh; }

/* 主卡片 */
.main-card { margin-bottom: 16px; }
.title-input :deep(.el-input__inner) { font-size: 16px; font-weight: 500; }
.meta { display: flex; gap: 12px; width: 100%; }
.meta-select { flex: 1; max-width: 220px; }

/* 富文本编辑器：高度随视口自适应，避免大块空白或过矮 */
.editor-box {
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  overflow: hidden; width: 100%;
}
.editor-toolbar { border-bottom: 1px solid var(--c-border); background: var(--c-surface-sub); }
.editor-body { min-height: clamp(320px, 46vh, 560px); }
.editor-body :deep(.w-e-text-container) { min-height: clamp(320px, 46vh, 560px); }

/* 右侧 AI 面板：仅大屏（lg+）吸顶跟随；中窄屏堆叠在全宽下 */
.side :deep(.el-card__body) { padding: 16px 20px 20px; }
@media (min-width: 1200px) {
  .side {
    position: sticky;
    top: calc(var(--header-h) + 12px);
  }
}
.side-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.side-title {
  display: inline-flex; align-items: center; gap: 6px;
  font-family: var(--font-display);
  font-size: 15px; font-weight: 600;
}
.side-title .el-icon { color: var(--c-primary); }

/* Tab 标签带图标 */
.side-tabs :deep(.el-tabs__header) { margin-bottom: 14px; }
.tab-label {
  display: inline-flex; align-items: center; gap: 5px;
}
.tab-label .el-icon { font-size: 14px; }

/* 快捷工具按钮：面板窄时单列竖排，面板宽（堆叠全宽）时自动一行三列；
   图标/文字间距交给 EP 内置规则（el-icon + span），loading 时文字位置不跳动 */
.ai-btns {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}
.ai-quick {
  width: 100%;
  justify-content: flex-start;
}

/* 问答行：输入框自适应、发送按钮固定不被压缩 */
.chat-row { display: flex; gap: 8px; }
.chat-row :deep(.el-input) { flex: 1; min-width: 0; }
.chat-send { flex-shrink: 0; width: 40px; }
.ai-busy-alert { margin-top: 12px; }

/* 任务中心 */
.mq-hint { margin: 0 0 12px; font-size: 12px; line-height: 1.6; color: var(--c-text-sub); }
.ai-async { display: flex; flex-direction: column; gap: 10px; }
.async-select { width: 100%; }
.async-question { width: 100%; }
.async-submit { width: 100%; }
.async-state { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.async-error { margin-top: 2px; }

/* AI 结果框 */
.ai-result {
  margin-top: 12px;
  background: var(--c-surface-sub);
  border: 1px solid var(--c-divider);
  border-radius: var(--r-md);
  padding: 10px 12px;
  max-height: 40vh; overflow: auto;
}
.ai-result pre { white-space: pre-wrap; word-break: break-word; font-size: 13px; line-height: 1.65; }
.insert-btn { margin-top: 10px; width: 100%; }

@media (max-width: 768px) {
  .meta { flex-direction: column; }
  .meta-select { max-width: none; }
}
</style>