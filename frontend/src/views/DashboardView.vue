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
        <div class="sider-links">
          <el-button text size="small" @click="router.push('/categories')">分类</el-button>
          <el-button text size="small" @click="router.push('/tags')">标签</el-button>
          <el-button text size="small" @click="router.push('/recycle')">回收站</el-button>
          <el-button text size="small" @click="router.push('/stats')">数据概览</el-button>
        </div>
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
          <el-card class="notes-card">
            <el-alert v-if="!currentKb" title="请先在左侧选择一个知识库" type="info" :closable="false" />
            <template v-else>
              <el-table :data="notes" v-loading="notesLoading" empty-text="暂无笔记">
                <el-table-column label="标题" min-width="140">
                  <template #default="{ row }">
                    <span v-if="row.highlight" class="tl" v-html="hlHtml(row.highlight)"></span>
                    <span v-else>{{ row.title }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="词数" width="80" align="center">
                  <template #default="{ row }">{{ row.wordCount }}</template>
                </el-table-column>
                <el-table-column label="浏览" width="90" align="center">
                  <template #default="{ row }">{{ row.viewCount }}</template>
                </el-table-column>
                <el-table-column label="操作" width="200">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="editNote(row.id)">编辑</el-button>
                    <el-button link type="success" @click="viewDetail(row.id)">详情</el-button>
                    <el-button link type="danger" @click="removeNote(row.id)">删</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </el-card>
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
import { useRouter } from 'vue-router'
import { Notebook, Sunny, Moon, Plus, Setting, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useTheme } from '@/composables/useTheme'
import { kbApi, noteApi, authApi } from '@/api'
import { THEME } from '@/constants'
import { hlHtml, initials } from '@/utils/format'

const router = useRouter()
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
  display: flex; align-items: center; gap: 8px;
  padding: 18px 16px 14px; color: var(--c-primary); font-weight: 700; font-size: 17px;
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
  display: flex; flex-wrap: wrap; gap: 2px;
  padding: 8px 10px; border-top: 1px solid var(--c-divider);
}

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
.rich img { max-width: 100%; }
.rich { line-height: 1.7; }

@media (max-width: 768px) {
  .layout { flex-direction: column; }
  .sider { width: 100% !important; border-right: none; border-bottom: 1px solid var(--c-border); max-height: 260px; }
  .kb-menu { max-height: 140px; }
}
</style>