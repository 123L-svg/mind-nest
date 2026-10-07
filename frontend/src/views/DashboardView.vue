<template>
  <div class="dash">
    <el-container class="layout">
      <!-- 左侧边栏：品牌 + 新建 + 知识库导航 + 用户 -->
      <el-aside class="sider" width="240px">
        <div class="sider-brand">
          <el-icon :size="22"><Notebook /></el-icon>
          <span class="brand">MindNest</span>
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

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailModal.show" :title="detailModal.title" width="720px"
               top="6vh" @closed="detailModal.show = false">
      <div class="rich" v-html="detailModal.html"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Notebook, Sunny, Moon, Plus, Setting, SwitchButton, Edit, Delete, Clock, View, Document, Folder, CollectionTag, TrendCharts } from '@element-plus/icons-vue'
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
const detailModal = ref({ show: false, title: '', html: '' })

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
  const res = await noteApi.detail(id)
  const d = res.data
  detailModal.value = { show: true, title: d.title, html: d.content || '<p>（无内容）</p>' }
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

/* 侧边栏 */
.sider {
  display: flex; flex-direction: column;
  background: var(--c-surface);
  border-right: 1px solid var(--c-border);
}
.sider-brand {
  display: flex; align-items: center; gap: 10px;
  padding: 18px 16px 14px; color: var(--c-text); font-weight: 700; font-size: 17px;
}
.sider-brand :deep(.el-icon) {
  width: 34px; height: 34px;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 10px;
  background: var(--grad-brand);
  color: #fff;
}
.sider-create { padding: 0 12px 10px; }
.sider-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 16px; color: var(--c-text-sub); font-size: 12px;
}
.kb-menu { flex: 1; overflow-y: auto; border-right: none; }
.kb-menu .el-menu-item { height: 46px; padding: 0 16px; }
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
.sider-link:hover { background: var(--c-surface-sub); color: var(--c-primary); }
.sider-link.active { background: var(--grad-brand-soft); color: var(--c-primary); font-weight: 600; }
.sider-link .el-icon { font-size: 15px; flex-shrink: 0; }

/* 顶部栏 */
.topbar {
  display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap;
  height: 56px; padding: 0 20px;
  background: var(--c-surface); border-bottom: 1px solid var(--c-border);
}
.topbar-left { display: flex; align-items: center; gap: 10px; min-width: 0; flex-wrap: wrap; }
.topbar-left .title {
  font-size: 16px; font-weight: 600; color: var(--c-text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.topbar-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.right-avatar { cursor: pointer; flex-shrink: 0; }
.muted { color: var(--c-text-sub); }

/* 内容区 */
.content { background: var(--c-bg); padding: 20px; }

/* 笔记卡片流 */
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
  cursor: pointer;
  min-height: 148px;
}
.sk-card { cursor: default; }
.note-title {
  font-size: 15px; font-weight: 600; color: var(--c-text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  padding-right: 44px;
}
.note-summary {
  flex: 1;
  font-size: 13px; line-height: 1.6; color: var(--c-text-sub);
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

.rich img { max-width: 100%; }
.rich { line-height: 1.7; }

@media (max-width: 768px) {
  .layout { flex-direction: column; }
  .sider { width: 100% !important; border-right: none; border-bottom: 1px solid var(--c-border); max-height: 260px; }
  .kb-menu { max-height: 140px; }
}
</style>