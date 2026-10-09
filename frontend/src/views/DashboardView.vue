<template>
  <div class="dash">
    <el-container class="layout">
      <!-- 左侧边栏：品牌 + 新建 + 知识库导航 + 用户 -->
      <el-aside class="sider" width="240px">
        <div class="sider-brand">
          <span class="seal">巢</span>
          <span class="brand">MindNest · 智巢</span>
        </div>
        <div class="sider-create">
          <el-input v-model="newKbName" size="small" placeholder="新建知识库"
                    clearable @keyup.enter="addKb">
            <template #append><el-button :icon="Plus" @click="addKb" /></template>
          </el-input>
        </div>
        <div class="sider-head">
          <span>我的知识库（{{ kbs.length }}）</span>
          <el-button text size="small" @click="loadKbs">刷新</el-button>
        </div>
        <el-menu :default-active="currentKb?.id?.toString() || ''" class="kb-menu"
                 @select="onSelectKb" v-loading="kbsLoading">
          <el-menu-item v-for="kb in kbs" :key="kb.id" :index="kb.id?.toString()">
            <div class="kb-item">
              <span class="kb-name">{{ kb.name }}</span>
              <span class="kb-meta">
                <el-tag v-if="kb.isPublic" size="small" type="success">公开</el-tag>
                <span class="kb-count">{{ kb.noteCount }}</span>
              </span>
            </div>
          </el-menu-item>
        </el-menu>
        <nav class="sider-links">
          <button v-for="l in links" :key="l.path" class="sider-link"
                  :class="{ active: route.path === l.path }" @click="router.push(l.path)">
            <el-icon><component :is="l.icon" /></el-icon>
            <span>{{ l.label }}</span>
          </button>
        </nav>
      </el-aside>

      <!-- 右侧：顶部栏 + 笔记内容 -->
      <el-container>
        <el-header class="topbar">
          <div class="topbar-left">
            <template v-if="currentKb">
              <span class="title">{{ currentKb.name }}</span>
              <el-tag :type="currentKb.isPublic ? 'success' : 'info'" size="small">
                {{ currentKb.isPublic ? '公开' : '私有' }}
              </el-tag>
              <el-button text size="small" @click="togglePublic(currentKb)">
                {{ currentKb.isPublic ? '设私有' : '公开' }}
              </el-button>
              <el-button v-if="currentKb.isPublic" text size="small" type="success"
                         @click="copyShare(currentKb)">分享</el-button>
              <el-button text size="small" type="danger" @click="removeKb(currentKb.id)">删除</el-button>
            </template>
            <span v-else class="muted">请先在左侧选择知识库</span>
          </div>
          <div class="topbar-right">
            <el-input v-model="noteKeyword" size="small" placeholder="搜索笔记…" clearable
                      style="width:200px" @keyup.enter="loadNotes" />
            <el-button size="small" @click="loadNotes">搜索</el-button>
            <el-button size="small" @click="clearSearch">重置</el-button>
            <el-button size="small" type="primary" :disabled="!currentKb" @click="createNote">
              新建笔记
            </el-button>
            <el-button size="small" @click="toggleTheme" :title="isDark ? '切换浅色' : '切换深色'">
              <el-icon><component :is="isDark ? Sunny : Moon" /></el-icon>
            </el-button>
            <el-dropdown class="user-dropdown" trigger="click" @command="onUserCommand">
              <el-avatar class="right-avatar" :size="32" :src="avatarUrl"
                         :title="user?.nickname || user?.username">{{ initials(user) }}</el-avatar>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">
                    <el-icon><Setting /></el-icon>设置
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>
                    <el-icon><SwitchButton /></el-icon>退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>

        <el-main class="content">
          <el-alert v-if="!currentKb" title="请先在左侧选择一个知识库" type="info" :closable="false" />
          <template v-else>
            <!-- 加载骨架屏 -->
            <div v-if="notesLoading" class="note-grid">
              <div v-for="i in 8" :key="i" class="note-card sk-card">
                <el-skeleton :rows="2" animated />
              </div>
            </div>

            <!-- 笔记卡片流 -->
            <div v-else-if="notes.length" class="note-grid">
              <div v-for="row in notes" :key="row.id" class="note-card card-hover"
                   @click="viewDetail(row.id)">
                <div class="note-ops">
                  <el-icon :size="15" title="编辑" @click.stop="editNote(row.id)"><Edit /></el-icon>
                  <el-icon :size="15" title="删除" class="op-danger" @click.stop="removeNote(row.id)">
                    <Delete />
                  </el-icon>
                </div>
                <div class="note-title">
                  <span v-if="row.highlight" class="tl" v-html="hlHtml(row.highlight)"></span>
                  <span v-else>{{ row.title || '（无标题）' }}</span>
                </div>
                <div class="note-summary">{{ row.summary || '暂无摘要，点击查看全文内容…' }}</div>
                <div class="note-tags" v-if="row.tags?.length">
                  <el-tag v-for="t in row.tags.slice(0, 3)" :key="t.id" size="small" effect="plain">
                    {{ t.name }}
                  </el-tag>
                </div>
                <div class="note-meta" :title="'更新于 ' + formatDateTime(row.updateTime)">
                  <span class="meta-item"><el-icon><Clock /></el-icon>{{ formatDate(row.updateTime) }}</span>
                  <span class="meta-item"><el-icon><View /></el-icon>{{ row.viewCount || 0 }}</span>
                  <span class="meta-item"><el-icon><Document /></el-icon>{{ row.wordCount || 0 }} 字</span>
                </div>
              </div>
            </div>

            <!-- 空状态引导 -->
            <div v-else class="empty-wrap">
              <AppEmpty :description="noteKeyword ? '没有找到相关笔记，换个关键词试试' : '这个知识库还没有笔记'">
                <el-button v-if="!noteKeyword" type="primary" @click="createNote">
                  <el-icon style="margin-right:4px"><Plus /></el-icon>写下第一篇笔记
                </el-button>
              </AppEmpty>
            </div>
          </template>
        </el-main>
      </el-container>
    </el-container>

    <!-- 详情弹窗：飞书文档风阅读态 -->
    <el-dialog v-model="detailModal.show" width="820px" top="6vh" class="doc-reader"
               :show-close="false" @closed="detailModal.show = false">
      <div v-if="detailModal.note" class="doc-viewer">
        <!-- 文档头：标题 + 元信息 + 操作 -->
        <header class="doc-header">
          <h2 class="doc-title">{{ detailModal.note.title || '（无标题）' }}</h2>
          <div class="doc-meta">
            <span class="meta-item" :title="'更新于 ' + formatDateTime(detailModal.note.updateTime)">
              <el-icon><Clock /></el-icon>{{ formatDate(detailModal.note.updateTime) }}
            </span>
            <span class="meta-item"><el-icon><View /></el-icon>{{ detailModal.note.viewCount || 0 }} 次浏览</span>
            <span class="meta-item"><el-icon><Document /></el-icon>{{ detailModal.note.wordCount || 0 }} 字</span>
            <el-tag v-for="t in detailModal.note.tags || []" :key="t.id" size="small" effect="plain">
              {{ t.name }}
            </el-tag>
          </div>
          <div class="doc-actions">
            <el-button type="primary" size="small" :icon="Edit" @click="editNote(detailModal.note.id)">
              编辑
            </el-button>
            <button class="doc-close" title="关闭 (Esc)" @click="detailModal.show = false">
              <el-icon><Close /></el-icon>
            </button>
          </div>
        </header>

        <!-- 摘要（可选）：金侧边引言块 -->
        <div v-if="detailModal.note.summary" class="doc-summary">{{ detailModal.note.summary }}</div>

        <!-- 正文：限宽阅读列 -->
        <div class="doc-body rich" v-html="detailModal.note.content || '<p>（无内容）</p>'"></div>

        <!-- 底部信息条 -->
        <footer class="doc-footer">
          创建于 {{ formatDateTime(detailModal.note.createTime) }} · 更新于 {{ formatDateTime(detailModal.note.updateTime) }}
        </footer>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Sunny, Moon, Plus, Setting, SwitchButton, Edit, Delete, Clock, View, Document, Folder, CollectionTag, TrendCharts, Close } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useTheme } from '@/composables/useTheme'
import { kbApi, noteApi, authApi } from '@/api'
import { THEME } from '@/constants'
import { hlHtml, initials, formatDate, formatDateTime } from '@/utils/format'
import AppEmpty from '@/components/AppEmpty.vue'

const router = useRouter()
const route = useRoute()

/* 侧边栏底部快捷入口 */
const links = [
  { path: '/categories', label: '分类', icon: Folder },
  { path: '/tags', label: '标签', icon: CollectionTag },
  { path: '/recycle', label: '回收站', icon: Delete },
  { path: '/stats', label: '数据概览', icon: TrendCharts },
]
const store = useUserStore()
const { theme, toggle: toggleTheme } = useTheme()
const isDark = computed(() => theme.value === THEME.DARK)
const avatarUrl = computed(() => {
  const a = user.value?.avatar
  return a ? '/api' + a : ''
})
const user = ref(null)
const kbs = ref([])
const notes = ref([])
const currentKb = ref(null)
const newKbName = ref('')
const noteKeyword = ref('')
const kbsLoading = ref(false)
const notesLoading = ref(false)
const detailModal = ref({ show: false, note: null })

async function refreshInfo() {
  try { user.value = await store.fetchInfo() } catch (e) { /* 401 已处理 */ }
}

async function loadKbs() {
  kbsLoading.value = true
  try { kbs.value = (await kbApi.list()).data } catch (e) { window.$message?.error(e.message) }
  finally { kbsLoading.value = false }
}

async function addKb() {
  if (!newKbName.value) return window.$message?.warning('请输入知识库名称')
  const name = newKbName.value.trim()
  newKbName.value = ''
  // 乐观更新：先把占位知识库即时加入列表，失败再回滚
  const temp = { id: null, name, description: '', isPublic: 0, noteCount: 0 }
  kbs.value = [...kbs.value, temp]
  try {
    const res = await kbApi.add({ name })
    const idx = kbs.value.indexOf(temp)
    if (idx >= 0) kbs.value[idx] = { ...kbs.value[idx], id: res.data }
    window.$message?.success('新建成功')
  } catch (e) {
    kbs.value = kbs.value.filter((k) => k !== temp) // 回滚
    window.$message?.error(e.message)
  }
}

async function removeKb(id) {
  try {
    await window.$confirm('确认删除该知识库？', '提示', { type: 'warning' })
  } catch { return }
  await kbApi.remove(id)
  if (currentKb.value?.id === id) currentKb.value = null
  await loadKbs()
}

async function togglePublic(kb) {
  // 乐观更新：本地即时切换，失败回退
  const next = kb.isPublic ? 0 : 1
  kb.isPublic = next
  try {
    await kbApi.update({ id: kb.id, name: kb.name, isPublic: next })
    window.$message?.success(next === 1 ? '已公开分享' : '已设为私有')
  } catch (e) {
    kb.isPublic = next ? 0 : 1
    window.$message?.error(e.message)
  }
}

function copyShare(kb) {
  const url = `${location.origin}/share/${kb.id}`
  navigator.clipboard?.writeText(url)
    .then(() => window.$message?.success('分享链接已复制：' + url))
    .catch(() => window.$message?.info('分享链接：' + url))
}

function onSelectKb(index) {
  const kb = kbs.value.find((k) => k.id === index)
  if (!kb) return
  currentKb.value = kb
  loadNotes()
}

async function loadNotes() {
  if (!currentKb.value) return
  notesLoading.value = true
  try {
    const res = await noteApi.list({
      kbId: currentKb.value.id,
      size: 50,
      keyword: noteKeyword.value || undefined
    })
    notes.value = res.data.records
  } finally {
    notesLoading.value = false
  }
}

function clearSearch() {
  noteKeyword.value = ''
  loadNotes()
}

async function viewDetail(id) {
  const d = (await noteApi.detail(id)).data
  // 详情接口已自增浏览量，直接展示返回值即可
  detailModal.value = { show: true, note: d }
  const row = notes.value.find((n) => n.id === id)
  if (row) row.viewCount = d.viewCount
}

async function removeNote(id) {
  try {
    await window.$confirm('确认删除该笔记？', '提示', { type: 'warning' })
  } catch { return }
  await noteApi.remove(id)
  await loadNotes()
}

function createNote() {
  if (!currentKb.value) return
  router.push({ path: '/note/edit', query: { kbId: currentKb.value.id } })
}
function editNote(id) { router.push({ path: '/note/edit', query: { id } }) }

function logout() {
  // 先调后端使当前 token 失效（加入 Redis 黑名单），再清本地并跳登录
  authApi.logout().catch(() => {})
  store.logout()
  router.push('/login')
}

function onUserCommand(cmd) {
  if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'logout') logout()
}

onMounted(() => { refreshInfo(); loadKbs() })
</script>

<style scoped>
.layout { min-height: 100vh; }
.dash { min-height: 100vh; }

/* ===== 侧边栏（绢帛） ===== */
.sider {
  display: flex; flex-direction: column;
  background: var(--c-surface-sub);
  border-right: 1px solid var(--c-border);
}
.sider-brand {
  display: flex; align-items: center; gap: 10px;
  padding: 18px 16px 14px;
}
.seal {
  width: 30px; height: 30px;
  display: inline-flex; align-items: center; justify-content: center;
  font-family: var(--font-display);
  font-size: 17px; font-weight: 700;
  color: #f7ecd7;
  background: #b03a2a;
  border-radius: 3px;
  box-shadow: inset 0 0 0 2px rgba(247, 236, 215, 0.25), 0 2px 8px rgba(176, 58, 42, 0.28);
}
.brand {
  font-family: var(--font-display);
  font-size: 16px; font-weight: 600; letter-spacing: 0.06em;
  color: var(--c-text);
}
.sider-create { padding: 0 12px 10px; }
.sider-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 16px; color: var(--c-text-sub); font-size: 12px;
  letter-spacing: 0.08em;
}
.kb-menu { flex: 1; overflow-y: auto; border-right: none; background: transparent; }
.kb-menu :deep(.el-menu-item) {
  height: 44px; padding: 0 14px; margin: 2px 8px;
  border-radius: var(--r-md);
}
.kb-menu :deep(.el-menu-item.is-active) {
  background: var(--grad-brand-soft);
}
.kb-menu :deep(.el-menu-item:hover) {
  background: var(--grad-brand-soft);
}
.kb-item { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.kb-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kb-meta { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.kb-count { color: var(--c-text-sub); font-size: 12px; }
.sider-links {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 10px 10px 12px;
  border-top: 1px solid var(--c-divider);
}
.sider-link {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  background: transparent;
  border-radius: var(--r-md);
  font-size: 13px;
  font-family: inherit;
  color: var(--c-text);
  cursor: pointer;
  transition: background var(--t-fast), color var(--t-fast);
}
.sider-link:hover { background: var(--grad-brand-soft); color: var(--c-primary); }
.sider-link.active { background: var(--grad-brand-soft); color: var(--c-primary); font-weight: 600; }
.sider-link .el-icon { font-size: 15px; flex-shrink: 0; }

/* ===== 顶部栏（宣纸） ===== */
.topbar {
  display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap;
  height: 56px; padding: 0 20px;
  background: var(--c-surface); border-bottom: 1px solid var(--c-border);
}
.topbar-left { display: flex; align-items: center; gap: 10px; min-width: 0; flex-wrap: wrap; }
.topbar-left .title {
  font-family: var(--font-display);
  font-size: 17px; font-weight: 600; letter-spacing: 0.04em;
  color: var(--c-text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  padding-left: 12px;
  position: relative;
}
.topbar-left .title::before {
  content: '';
  position: absolute; left: 0; top: 50%;
  transform: translateY(-50%);
  width: 3px; height: 16px;
  background: var(--c-primary);
  border-radius: 1px;
}
.topbar-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.right-avatar { cursor: pointer; flex-shrink: 0; }
.muted { color: var(--c-text-sub); }

/* ===== 内容区 ===== */
.content { background: var(--c-bg); padding: 20px; }

/* ===== 笔记卡片流（宣纸卡片） ===== */
.note-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 14px;
}
.note-card {
  position: relative;
  display: flex; flex-direction: column; gap: 10px;
  padding: 16px 16px 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-md);
  cursor: pointer;
  min-height: 148px;
  transition: transform var(--t-base), box-shadow var(--t-base), border-color var(--t-base);
}
.note-card:hover {
  transform: translateY(-3px);
  border-color: var(--c-primary);
  box-shadow: var(--shadow-hover);
}
.sk-card { cursor: default; }
.sk-card:hover { transform: none; border-color: var(--c-border); box-shadow: var(--shadow-md); }
.note-title {
  font-family: var(--font-display);
  font-size: 16px; font-weight: 600; letter-spacing: 0.02em;
  color: var(--c-text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  padding-right: 44px;
}
.note-summary {
  flex: 1;
  font-size: 13px; line-height: 1.7; color: var(--c-text-sub);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.note-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.note-meta {
  display: flex; align-items: center; gap: 14px;
  padding-top: 10px;
  border-top: 1px solid var(--c-divider);
  color: var(--c-text-sub); font-size: 12px;
}
.meta-item { display: inline-flex; align-items: center; gap: 4px; }
.meta-item .el-icon { font-size: 13px; }

/* 卡片悬浮操作按钮 */
.note-ops {
  position: absolute; top: 12px; right: 12px;
  display: flex; gap: 8px;
  color: var(--c-text-sub);
  opacity: 0;
  transition: opacity var(--t-fast);
}
.note-card:hover .note-ops { opacity: 1; }
.note-ops .el-icon { cursor: pointer; padding: 3px; border-radius: var(--r-sm); }
.note-ops .el-icon:hover { color: var(--c-primary); background: var(--c-surface-sub); }
.note-ops .op-danger:hover { color: var(--c-danger); }

/* 空状态 */
.empty-wrap {
  display: flex; justify-content: center;
  padding: 60px 0;
}

/* ===== 详情弹窗：飞书文档风阅读态 ===== */
.doc-viewer {
  display: flex;
  flex-direction: column;
  max-height: 82vh;
  background: var(--c-surface);
  border-radius: 14px;
  overflow: hidden;
}

/* 文档头：标题 + 元信息，操作区悬浮右上 */
.doc-header {
  position: relative;
  flex-shrink: 0;
  padding: 24px 28px 16px;
  border-bottom: 1px solid var(--c-divider);
  background: var(--c-surface);
}
.doc-title {
  margin: 0 128px 10px 0;
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1.4;
  color: var(--c-text);
  word-break: break-word;
}
.doc-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--c-text-sub);
}
.doc-actions {
  position: absolute;
  top: 20px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.doc-close {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 50%;
  color: var(--c-text-sub);
  font-size: 16px;
  cursor: pointer;
  transition: background var(--t-fast), color var(--t-fast);
}
.doc-close:hover { background: var(--c-surface-sub); color: var(--c-text); }

/* 摘要：金侧边引言块 */
.doc-summary {
  flex-shrink: 0;
  margin: 16px 28px 0;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--c-text-sub);
  background: var(--grad-brand-soft);
  border-left: 3px solid var(--c-primary);
  border-radius: 0 var(--r-md) var(--r-md) 0;
}

/* 正文：内部滚动的阅读列 */
.doc-body {
  flex: 1;
  min-height: 120px;
  overflow-y: auto;
  padding: 24px 32px 28px;
  font-size: 15px;
  line-height: 1.85;
  color: var(--c-text);
}
/* 富文本排版层级（v-html 内容需 :deep 穿透） */
.doc-body :deep(p) { margin: 0 0 12px; }
.doc-body :deep(h1) { margin: 6px 0 14px; font-family: var(--font-display); font-size: 22px; font-weight: 700; line-height: 1.4; }
.doc-body :deep(h2) { margin: 20px 0 12px; font-family: var(--font-display); font-size: 19px; font-weight: 700; line-height: 1.4; }
.doc-body :deep(h3) { margin: 18px 0 10px; font-family: var(--font-display); font-size: 17px; font-weight: 600; line-height: 1.4; }
.doc-body :deep(h4), .doc-body :deep(h5), .doc-body :deep(h6) { margin: 16px 0 8px; font-size: 15.5px; font-weight: 600; }
.doc-body :deep(h1:first-child), .doc-body :deep(h2:first-child),
.doc-body :deep(h3:first-child), .doc-body :deep(h4:first-child) { margin-top: 0; }
.doc-body :deep(ul), .doc-body :deep(ol) { margin: 0 0 12px; padding-left: 1.6em; }
.doc-body :deep(li) { margin: 4px 0; }
.doc-body :deep(li > p) { margin: 0 0 4px; }
.doc-body :deep(blockquote) {
  margin: 0 0 12px;
  padding: 10px 16px;
  border-left: 3px solid var(--c-primary);
  background: var(--grad-brand-soft);
  border-radius: 0 var(--r-md) var(--r-md) 0;
  color: var(--c-text-sub);
}
.doc-body :deep(code) {
  font-family: Consolas, Monaco, monospace;
  font-size: 0.9em;
  padding: 2px 6px;
  background: var(--c-surface-sub);
  border-radius: var(--r-sm);
}
.doc-body :deep(pre) {
  margin: 0 0 12px;
  padding: 14px 16px;
  background: var(--c-surface-sub);
  border-radius: var(--r-md);
  overflow-x: auto;
}
.doc-body :deep(pre code) { padding: 0; background: transparent; }
.doc-body :deep(a) { color: var(--c-primary); text-decoration: none; }
.doc-body :deep(a:hover) { text-decoration: underline; }
.doc-body :deep(img) { max-width: 100%; margin: 6px 0; border-radius: var(--r-md); }
.doc-body :deep(table) { width: 100%; margin: 0 0 12px; border-collapse: collapse; font-size: 14px; }
.doc-body :deep(th), .doc-body :deep(td) { padding: 8px 12px; border: 1px solid var(--c-border); text-align: left; }
.doc-body :deep(th) { background: var(--c-surface-sub); font-weight: 600; }
.doc-body :deep(hr) { margin: 20px 0; border: none; height: 1px; background: var(--c-divider); }
.doc-body :deep(:last-child) { margin-bottom: 0; }

/* 底部信息条 */
.doc-footer {
  flex-shrink: 0;
  padding: 10px 28px;
  border-top: 1px solid var(--c-divider);
  font-size: 12px;
  letter-spacing: 0.02em;
  color: var(--c-text-sub);
  background: var(--c-surface);
}

@media (max-width: 768px) {
  .layout { flex-direction: column; }
  .sider { width: 100% !important; border-right: none; border-bottom: 1px solid var(--c-border); max-height: 260px; }
  .kb-menu { max-height: 140px; }
}
</style>

<style>
/* 详情弹窗外壳（el-dialog teleport 到 body，需全局样式）：
   大圆角卡片、隐藏默认标题栏与内边距，交由 .doc-viewer 接管布局 */
.el-dialog.doc-reader {
  padding: 0;
  border-radius: 14px;
  overflow: hidden;
  background: var(--c-surface);
  box-shadow: var(--shadow-lg);
}
.doc-reader .el-dialog__header { display: none; }
.doc-reader .el-dialog__body { padding: 0; }
@media (max-width: 900px) {
  .el-dialog.doc-reader { width: 94% !important; }
}
</style>