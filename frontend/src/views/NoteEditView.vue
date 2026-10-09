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
                  <!-- 复制按钮：hover 气泡时浮现 -->
                  <el-tooltip v-if="m.role === 'ai' && !m.pending" content="复制" placement="top">
                    <el-button class="copy-btn" link size="small" @click="copyMsgText(m.text)">
                      <el-icon><CopyDocument /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <pre>{{ m.pending ? 'AI 处理中…' : m.text }}</pre>
                  <!-- 大纲结果：一键插入正文 -->
                  <el-button v-if="m.role === 'ai' && m.action === 'outline' && !m.pending && !m.error"
                             type="primary" size="small" class="insert-btn"
                             @click="insertOutline(m.text)">插入正文</el-button>
                  <!-- 润色结果：打开 Diff 对比，逐处选择后应用 -->
                  <el-button v-if="m.role === 'ai' && m.action === 'polish' && !m.pending && !m.error"
                             type="success" size="small" class="insert-btn"
                             @click="openPolishDiffFromMsg(m)">对比应用</el-button>
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

            <!-- 快捷功能工具条（下拉选参数后执行） -->
            <div class="chat-tools">
              <el-dropdown trigger="click" :disabled="aiBusy || chatStreaming || !form.content"
                           @command="(t) => submitAiAsync('outline', { type: t })">
                <button class="chat-tool" :disabled="aiBusy || chatStreaming || !form.content">
                  <el-icon><List /></el-icon>大纲
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="auto">智能生成</el-dropdown-item>
                    <el-dropdown-item command="organize">整理已有内容</el-dropdown-item>
                    <el-dropdown-item command="creative">基于标题创作</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-dropdown trigger="click" :disabled="aiBusy || chatStreaming || !form.content"
                           @command="(s) => submitAiAsync('polish', s ? { style: s } : {})">
                <button class="chat-tool" :disabled="aiBusy || chatStreaming || !form.content">
                  <el-icon><MagicStick /></el-icon>润色
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="">通用润色</el-dropdown-item>
                    <el-dropdown-item command="concise">精简凝练</el-dropdown-item>
                    <el-dropdown-item command="formal">正式严谨</el-dropdown-item>
                    <el-dropdown-item command="vivid">生动活泼</el-dropdown-item>
                    <el-dropdown-item command="academic">学术化</el-dropdown-item>
                    <el-dropdown-item command="casual">口语化</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-dropdown trigger="click" :disabled="aiBusy || chatStreaming || !form.content"
                           @command="(l) => submitAiAsync('summarize', { length: l })">
                <button class="chat-tool" :disabled="aiBusy || chatStreaming || !form.content">
                  <el-icon><Document /></el-icon>摘要
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="short">一句话摘要</el-dropdown-item>
                    <el-dropdown-item command="medium">一段话摘要</el-dropdown-item>
                    <el-dropdown-item command="long">详细摘要</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>

            <!-- 输入区 -->
            <div class="chat-input">
              <el-input v-model="chatQuestion" type="textarea" :rows="2" resize="none"
                        placeholder="随便聊点什么，Enter 发送，Shift+Enter 换行"
                        @keydown.enter.exact.prevent="askChat" />
              <!-- 流式生成中显示「停止」，否则显示「发送」 -->
              <el-button v-if="!chatStreaming" type="primary" class="chat-send" :icon="Promotion"
                         :loading="aiBusy" :disabled="!chatQuestion" @click="askChat" />
              <el-button v-else type="danger" class="chat-send" :icon="VideoPause"
                         title="停止生成" @click="stopChat" />
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

    <!-- 润色对比（Diff）弹窗：句子级对比，点击高亮切换保留润色/原文 -->
    <el-dialog v-model="diffDialog.show" title="润色对比" width="740px" top="8vh"
               :close-on-click-modal="false">
      <div class="diff-toolbar">
        <span class="diff-legend">
          <span class="dot dot-new"></span>采用润色
          <span class="dot dot-old"></span>保留原文
          <span class="diff-hint">点击高亮片段切换；共 {{ diffDialog.count }} 处修改</span>
        </span>
        <el-dropdown trigger="click" @command="retryPolishStyle">
          <el-button size="small" :disabled="aiBusy">换风格重润<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="">通用润色</el-dropdown-item>
              <el-dropdown-item command="concise">精简凝练</el-dropdown-item>
              <el-dropdown-item command="formal">正式严谨</el-dropdown-item>
              <el-dropdown-item command="vivid">生动活泼</el-dropdown-item>
              <el-dropdown-item command="academic">学术化</el-dropdown-item>
              <el-dropdown-item command="casual">口语化</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div class="diff-body">
        <template v-for="(s, i) in diffDialog.segments" :key="i">
          <span v-if="s.type === 'same'">{{ s.text }}</span>
          <span v-else class="diff-seg" :class="s.keepNew ? 'keep-new' : 'keep-old'"
                title="点击切换 保留润色 / 保留原文" @click="toggleDiffSeg(i)">{{
            s.keepNew ? (s.add || '〔已删除〕') : (s.del || '〔不采用〕')
          }}</span>
        </template>
      </div>
      <template #footer>
        <span class="diff-scope-hint">{{
          diffDialog.scope === 'selection' ? '将应用到选中的文字片段' : '将替换笔记全文（保存后原内容存入历史版本）'
        }}</span>
        <el-button @click="diffDialog.show = false">取消</el-button>
        <el-button type="primary" @click="applyPolishDiff">应用到笔记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, shallowRef, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import { Boot, SlateTransforms } from '@wangeditor/editor'
import { List, MagicStick, Document, ArrowLeft, ArrowDown, ChatDotRound, Promotion, CopyDocument, VideoPause } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { kbApi, categoryApi, noteApi, aiApi, fileApi, exportApi } from '@/api'
import '@wangeditor/editor/dist/css/style.css'

// ============ 选中润色：自定义悬浮工具栏按钮（选中文本时出现） ============
class AiPolishMenu {
  constructor() {
    this.title = 'AI 润色选中文字'
    this.tag = 'button'
    this.iconSvg = '<svg viewBox="0 0 1024 1024" width="1em" height="1em"><path d="M512 96l58 168 168 58-168 58-58 168-58-168-168-58 168-58L512 96z" fill="currentColor"/><path d="M790 588l38 110 110 38-110 38-38 110-38-110-110-38 110-38 38-110z" fill="currentColor"/><path d="M330 784c22 0 40 14 46 36l12 42-42-12c-22-6-36-24-36-46v-20h20z" fill="currentColor"/></svg>'
  }
  getValue() { return '' }
  isActive() { return false }
  isDisabled() { return false }
  exec() { window.dispatchEvent(new CustomEvent('ai-polish-selection')) }
}
try {
  Boot.registerMenu({ key: 'aiPolishSelection', factory: () => new AiPolishMenu() })
} catch (e) { /* HMR 重复注册忽略 */ }

// ============ 句子级 Diff（LCS 对齐，用于润色对比） ============
/** 按中英文句末标点/换行切句（保留分隔符），末尾无标点片段也算一句 */
function splitSentences(text) {
  if (!text) return []
  return text.match(/[\s\S]*?[。！？!?；;\n]|[\s\S]+$/g) || []
}

/**
 * LCS 对齐原文与润色文的句子，返回对比段列表：
 * {type:'same', text} 未改动；{type:'change', del, add, keepNew} 可点击切换保留原文/润色
 */
function diffSentences(orig, polished) {
  const a = splitSentences(orig)
  const b = splitSentences(polished)
  // 超长文本退化为整体一组，避免 O(n²) 性能问题
  if (a.length > 400 || b.length > 400) {
    return [{ type: 'change', del: orig, add: polished, keepNew: true }]
  }
  const n = a.length, m = b.length
  // dp[i][j] = a[i..] 与 b[j..] 的最长公共子序列长度
  const dp = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0))
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1])
    }
  }
  // 回溯得到操作序列，相邻增删合并为一个可切换的修改组
  const segs = []
  const pushSame = (t) => {
    const last = segs[segs.length - 1]
    if (last && last.type === 'same') last.text += t
    else segs.push({ type: 'same', text: t })
  }
  const pushChange = (del, add) => {
    const last = segs[segs.length - 1]
    if (last && last.type === 'change') { last.del += del; last.add += add }
    else segs.push({ type: 'change', del, add, keepNew: true })
  }
  let i = 0, j = 0
  while (i < n && j < m) {
    if (a[i] === b[j]) { pushSame(a[i]); i++; j++ }
    else if (dp[i + 1][j] >= dp[i][j + 1]) { pushChange(a[i], ''); i++ }
    else { pushChange('', b[j]); j++ }
  }
  while (i < n) { pushChange(a[i], ''); i++ }
  while (j < m) { pushChange('', b[j]); j++ }
  return segs
}

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
  // 选中文本时的悬浮工具栏：首位加入自定义「AI 润色」按钮
  hoverbarKeys: {
    text: {
      menuKeys: [
        'aiPolishSelection', '|',
        'headerSelect', 'insertLink', 'bulletedList', '|',
        'bold', 'through', 'color', 'bgColor', 'clearStyle'
      ]
    }
  },
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

/** 悬浮工具栏「AI 润色」入口：润色当前选中文本（结果经 Diff 对比后回填） */
function polishSelection() {
  const ed = editorRef.value
  if (!ed) return
  if (aiBusy.value) return window.$message?.warning('AI 正在处理中，请稍候')
  const selText = ed.getSelectionText()
  if (!selText || !selText.trim()) return window.$message?.warning('请先选中要润色的文字')
  const sel = ed.selection ? JSON.parse(JSON.stringify(ed.selection)) : null
  submitAiAsync('polish', { scope: 'selection' }, { orig: selText, sel })
}

// ============ 润色对比（Diff）弹窗 ============
const diffDialog = ref({
  show: false, scope: 'full', orig: '', polished: '', segments: [], sel: null, snapshot: '', count: 0
})

/** 打开 Diff 弹窗：句子级对齐原文与润色文 */
function openPolishDiff({ scope = 'full', orig = '', polished = '', sel = null, snapshot = '' }) {
  const segments = diffSentences(orig, polished)
  const count = segments.filter((s) => s.type === 'change').length
  diffDialog.value = { show: true, scope, orig, polished, segments, sel, snapshot, count }
}

/** 气泡上的「对比应用」按钮入口（旧消息缺 orig 时用当前全文兜底） */
function openPolishDiffFromMsg(m) {
  openPolishDiff({
    scope: m.params?.scope || 'full',
    orig: m.orig || plainText(),
    polished: m.text,
    sel: m.sel || null,
    snapshot: m.snapshot || ''
  })
}

/** 点击修改组：切换 保留润色 / 保留原文 */
function toggleDiffSeg(idx) {
  const seg = diffDialog.value.segments[idx]
  if (seg?.type === 'change') seg.keepNew = !seg.keepNew
}

/** 按当前开关状态合成最终文本 */
function diffFinalText() {
  return diffDialog.value.segments
    .map((s) => (s.type === 'same' ? s.text : (s.keepNew ? s.add : s.del)))
    .join('')
}

/** 应用对比结果到编辑器：选中片段回填原位置，全文则整体替换 */
function applyPolishDiff() {
  const ed = editorRef.value
  if (!ed) return
  const d = diffDialog.value
  const html = markdownToHtml(diffFinalText())
  if (d.scope === 'selection' && d.sel) {
    let restored = false
    try {
      // 恢复提交润色时保存的选区，删除原选中片段后插入润色结果
      SlateTransforms.select(ed, d.sel)
      ed.deleteFragment()
      restored = true
    } catch (e) { restored = false }
    if (!restored) window.$message?.warning('选区已失效，润色结果将插入光标处')
    else if (d.snapshot && d.snapshot !== plainText()) {
      window.$message?.info('应用期间笔记内容有变动，请检查插入位置')
    }
    ed.dangerouslyInsertHtml(html)
    form.value.content = ed.getHtml()
    window.$message?.success('润色结果已应用到选中文字')
  } else {
    ed.clear()
    ed.dangerouslyInsertHtml(html)
    form.value.content = ed.getHtml()
    window.$message?.success('已替换全文，保存后原内容自动存入历史版本')
  }
  d.show = false
}

/** 换风格重新润色：复用同一原文与选区，重提交异步任务 */
function retryPolishStyle(style) {
  const d = diffDialog.value
  d.show = false
  submitAiAsync('polish', { style, scope: d.scope }, { orig: d.orig, sel: d.sel })
}

// ============ AI 对话 ============
const chatMsgs = ref([])          // {role:'user'|'ai', text, action, pending, error}
const chatListRef = ref(null)
const ACTION_LABELS = { outline: '生成大纲', polish: '润色内容', summarize: '生成摘要', chat: '内容问答' }
const POLISH_STYLE_LABELS = { concise: '精简凝练', formal: '正式严谨', vivid: '生动活泼', academic: '学术化', casual: '口语化' }
const SUMMARY_LENGTH_LABELS = { short: '一句话', medium: '一段话', long: '详细' }

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

/** 复制 AI 回复文本到剪贴板（clipboard API + execCommand 兜底） */
async function copyMsgText(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    ta.remove()
  }
  window.$message?.success('已复制到剪贴板')
}

/** 统一 AI 动作：提交异步 MQ + 轮询，结果以对话消息气泡呈现
 * ctx（选中润色时传入）：{ orig: 选中文本, sel: 编辑器选区(Slate range) } */
async function submitAiAsync(action, params = {}, ctx = null) {
  if (action !== 'chat' && !form.value.content) return
  if (action === 'chat' && !chatQuestion.value) return
  const question = action === 'chat' ? chatQuestion.value : null
  if (action === 'chat') chatQuestion.value = ''
  const snapshot = plainText()
  // 选中润色：内容用选中文本，并保存选区供结果回填
  let content = snapshot
  let sel = ctx?.sel || null
  if (action === 'polish' && params.scope === 'selection') {
    content = ctx?.orig ?? editorRef.value?.getSelectionText() ?? ''
    if (!sel) {
      const cur = editorRef.value?.selection
      sel = cur ? JSON.parse(JSON.stringify(cur)) : null
    }
    if (!content) return
  }
  aiBusy.value = true
  ai.value = { ...ai.value, result: '', action }
  let paramsDesc = ''
  if (action === 'outline') {
    if (params.type === 'creative') paramsDesc = '（创作型）'
    else if (params.type === 'organize') paramsDesc = '（整理型）'
  } else if (action === 'polish') {
    const parts = [
      params.scope === 'selection' ? '选中片段' : '',
      params.style ? (POLISH_STYLE_LABELS[params.style] || '') : ''
    ].filter(Boolean)
    if (parts.length) paramsDesc = `（${parts.join('·')}）`
  } else if (action === 'summarize' && params.length) {
    paramsDesc = `（${SUMMARY_LENGTH_LABELS[params.length] || ''}）`
  }
  chatMsgs.value.push({ role: 'user', text: action === 'chat' ? question : '✦ ' + ACTION_LABELS[action] + paramsDesc })
  const idx = chatMsgs.value.push({ role: 'ai', text: '', pending: true }) - 1
  scrollChatToBottom()
  try {
    const res = await aiApi.asyncSubmit({
      action,
      noteId: form.value.id,
      title: form.value.title,
      content,
      question,
      params
    })
    const task = await pollTask(res.data.taskId)
    if (task && task.status === 3) {
      chatMsgs.value[idx] = { role: 'ai', text: task.errorMsg || 'AI 任务处理失败', error: true, action, params, orig: content, sel, snapshot }
    } else if (task) {
      chatMsgs.value[idx] = { role: 'ai', text: task.result, action, params, orig: content, sel, snapshot }
      if (action === 'summarize') form.value.summary = task.result
      // 润色完成：自动打开 Diff 对比弹窗
      if (action === 'polish') {
        openPolishDiff({ scope: params.scope || 'full', orig: content, polished: task.result, sel, snapshot })
      }
    }
  } catch (e) {
    chatMsgs.value[idx] = { role: 'ai', text: e.message, error: true, action, params, orig: content, sel, snapshot }
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

// ============ chat 流式对话（SSE 逐字输出） ============
const chatStreaming = ref(false)
let chatAbort = null
let lastStreamScroll = 0

/** 停止生成：中断 SSE 流，已生成部分保留 */
function stopChat() { chatAbort?.abort() }

/** 发送 chat：走 SSE 流式接口，气泡逐字上屏；完成后后端写回多轮记忆 */
async function askChat() {
  if (chatStreaming.value || aiBusy.value || !chatQuestion.value) return
  const question = chatQuestion.value
  chatQuestion.value = ''
  chatMsgs.value.push({ role: 'user', text: question })
  const idx = chatMsgs.value.push({ role: 'ai', text: '' }) - 1
  chatStreaming.value = true
  scrollChatToBottom()
  chatAbort = new AbortController()
  try {
    const full = await aiApi.chatStream({
      noteId: form.value.id,
      title: form.value.title,
      content: plainText(),
      question
    }, {
      onDelta: (t) => {
        chatMsgs.value[idx].text += t
        // 滚动节流：避免每个 delta 都触发滚动
        const now = Date.now()
        if (now - lastStreamScroll > 200) {
          lastStreamScroll = now
          scrollChatToBottom()
        }
      },
      signal: chatAbort.signal
    })
    if (full) chatMsgs.value[idx].text = full
  } catch (e) {
    if (e.name === 'AbortError') {
      chatMsgs.value[idx].text += (chatMsgs.value[idx].text ? '\n\n' : '') + '（已停止生成）'
    } else {
      chatMsgs.value[idx] = { role: 'ai', text: e.message, error: true }
    }
  } finally {
    chatStreaming.value = false
    chatAbort = null
    scrollChatToBottom()
  }
}

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
  chatAbort?.abort()   // 流式对话进行中离开页面：中断生成
  window.removeEventListener('ai-polish-selection', polishSelection)
  editorRef.value?.destroy()
})
onMounted(async () => {
  window.addEventListener('ai-polish-selection', polishSelection)
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
  position: relative;
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
/* 复制按钮：气泡右下角，hover 气泡时浮现 */
.copy-btn {
  position: absolute;
  bottom: 2px;
  right: 2px;
  opacity: 0;
  transition: opacity 0.2s;
  color: var(--c-text-3, #909399);
}
.chat-bubble:hover .copy-btn { opacity: 1; }
.copy-btn:hover { color: var(--c-primary, #b88230); }

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

/* 润色对比（Diff）弹窗 */
.diff-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.diff-legend { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--c-text-2, #909399); }
.diff-legend .dot { width: 10px; height: 10px; border-radius: 3px; display: inline-block; }
.dot-new { background: #e1f3d9; box-shadow: inset 0 0 0 1px #67c23a; }
.dot-old { background: #fdf6ec; box-shadow: inset 0 0 0 1px #e6a23c; }
.diff-hint { margin-left: 6px; color: var(--c-text-3, #c0c4cc); }
.diff-body {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 2;
  max-height: 55vh;
  overflow-y: auto;
  padding: 14px 16px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-md, 8px);
  font-size: 14px;
}
.diff-seg { cursor: pointer; border-radius: 4px; padding: 1px 2px; user-select: none; }
.diff-seg.keep-new { background: #e1f3d9; color: #2f6000; box-shadow: inset 0 -2px 0 #67c23a; }
.diff-seg.keep-old { background: #fdf6ec; color: #8a5a00; box-shadow: inset 0 -2px 0 #e6a23c; }
.diff-scope-hint { display: inline-block; margin-right: 12px; font-size: 12px; color: var(--c-text-3, #c0c4cc); }

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