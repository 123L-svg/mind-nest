<template>
  <div class="editor-page">
    <el-header class="bar">
      <el-button @click="back">返回</el-button>
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
      <el-row :gutter="16">
        <el-col :xs="24" :md="18">
          <el-card class="main-card">
            <el-form label-position="top">
              <el-form-item label="笔记标题">
                <el-input v-model="form.title" placeholder="笔记标题" clearable />
              </el-form-item>
              <el-form-item>
                <div class="meta">
                  <el-select v-model="form.kbId" placeholder="选择知识库" style="width:200px"
                             @change="loadCategories">
                    <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
                  </el-select>
                  <el-select v-model="form.categoryId" placeholder="选择分类（可选）" clearable
                             style="width:200px">
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

        <el-col :xs="24" :md="6">
          <el-card class="side">
            <template #header><b>AI 助手</b></template>
            <el-tag v-if="ai.mock" type="warning" size="small" style="margin-bottom:12px">演示模式（mock）</el-tag>
            <div class="ai-btns">
              <el-button style="width:100%" :loading="aiBusy" @click="genOutline">✍ 生成大纲</el-button>
              <el-button style="width:100%" :loading="aiBusy" :disabled="!form.content" @click="polish">✨ 润色内容</el-button>
              <el-button style="width:100%" :loading="aiBusy" :disabled="!form.content" @click="summarize">📋 生成摘要</el-button>
            </div>
            <el-input v-if="!aiBusy" v-model="chatQuestion" placeholder="基于内容提问，回车发送"
                      clearable @keyup.enter="askChat" :disabled="!form.content" />
            <el-alert v-if="aiBusy" title="AI 处理中..." type="info" :closable="false" style="margin-top:10px" />
            <div v-if="ai.result && !aiBusy" class="ai-result">
              <pre>{{ ai.result }}</pre>
              <el-button v-if="ai.action === 'outline'" type="primary" size="small"
                         class="insert-btn" @click="insertOutline">插入正文</el-button>
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
import { ref, shallowRef, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
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

function applyAi(res, fn) {
  ai.value = { action: res.data.action, mock: res.data.mock, result: res.data.result }
  if (fn) fn(res.data.result)
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

async function genOutline() {
  aiBusy.value = true; ai.value = { ...ai.value, result: '' }
  try { const res = await aiApi.outline({ title: form.value.title, content: plainText() }); applyAi(res) }
  catch (e) { window.$message?.error(e.message) } finally { aiBusy.value = false }
}
async function polish() {
  aiBusy.value = true; ai.value = { ...ai.value, result: '' }
  try { const res = await aiApi.polish({ content: plainText() }); applyAi(res) }
  catch (e) { window.$message?.error(e.message) } finally { aiBusy.value = false }
}
async function summarize() {
  aiBusy.value = true; ai.value = { ...ai.value, result: '' }
  try { const res = await aiApi.summarize({ content: plainText() }); applyAi(res, (txt) => { form.value.summary = txt }) }
  catch (e) { window.$message?.error(e.message) } finally { aiBusy.value = false }
}
async function askChat() {
  if (!chatQuestion.value) return
  aiBusy.value = true; ai.value = { ...ai.value, result: '' }
  try { const res = await aiApi.chat({ content: plainText(), question: chatQuestion.value }); applyAi(res) }
  catch (e) { window.$message?.error(e.message) } finally { aiBusy.value = false }
}

onBeforeUnmount(() => {
  clearTimeout(autoSaveTimer)
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
.bar { display: flex; align-items: center; gap: 12px; border-bottom: 1px solid var(--c-border); }
.edit-title { flex: 1; font-weight: 600; }
.body { background: #f5f7fa; }
.main-card { margin-bottom: 16px; }
.meta { display: flex; gap: 12px; width: 100%; }
.editor-box { border: 1px solid #dcdfe6; border-radius: 6px; overflow: hidden; width: 100%; }
.editor-toolbar { border-bottom: 1px solid var(--c-border); }
.editor-body { min-height: 380px; }
.editor-body :deep(.w-e-text-container) { min-height: 380px; }
.side { position: sticky; top: 12px; }
.ai-btns { display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px; }
.ai-result { margin-top: 12px; background: #f5f7fa; border-radius: 6px; padding: 10px; max-height: 40vh; overflow: auto; }
.ai-result pre { white-space: pre-wrap; word-break: break-word; font-size: 13px; }
.insert-btn { margin-top: 10px; width: 100%; }
</style>