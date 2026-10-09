<template>
  <div class="share-wrap">
    <!-- ============ 阅读态：大标题 + 富文本正文（参考笔记编辑页排版） ============ -->
    <template v-if="current">
      <div class="reader">
        <div class="reader-top">
          <el-button link :icon="ArrowLeft" @click="backToList">返回列表</el-button>
          <span class="reader-kb">{{ kb?.name }}</span>
        </div>
        <h1 class="reader-title">{{ current.title }}</h1>
        <div class="reader-meta">
          <span class="meta-item"><el-icon><Clock /></el-icon>{{ formatDateTime(current.updateTime) }}</span>
          <span class="meta-item"><el-icon><View /></el-icon>{{ current.viewCount || 0 }} 次浏览</span>
          <span class="meta-item"><el-icon><Document /></el-icon>{{ current.wordCount || 0 }} 字</span>
        </div>
        <el-divider />
        <div class="rich reader-body" v-html="current.content || '<p>（无内容）</p>'"></div>
      </div>
    </template>

    <!-- ============ 列表态：知识库信息 + 搜索 + 笔记卡片网格（参考工作台卡片） ============ -->
    <template v-else>
      <el-card class="head" v-if="kb">
        <template #header><h1>{{ kb.name }}</h1></template>
        <p v-if="kb.description" class="muted">{{ kb.description }}</p>
        <p class="muted">· 公开分享 · {{ notes.length }} 篇笔记</p>
      </el-card>

      <el-card class="search">
        <div class="search-row">
          <el-input v-model="keyword" placeholder="搜索笔记标题/摘要/正文，回车搜索" clearable
                    @keyup.enter="loadNotes" />
          <el-button type="primary" @click="loadNotes">搜索</el-button>
        </div>
      </el-card>

      <!-- 笔记卡片网格 -->
      <div v-if="notes.length" class="note-grid">
        <div v-for="n in notes" :key="n.id" class="note-card card-hover" @click="openNote(n.id)">
          <div class="note-title">
            <span v-if="n.highlight" class="hl" v-html="hlHtml(n.highlight)"></span>
            <span v-else>{{ n.title || '（无标题）' }}</span>
          </div>
          <div class="note-summary">{{ n.summary || '暂无摘要，点击查看全文内容…' }}</div>
          <div class="note-meta" :title="'更新于 ' + formatDateTime(n.updateTime)">
            <span class="meta-item"><el-icon><Clock /></el-icon>{{ formatDate(n.updateTime) }}</span>
            <span class="meta-item"><el-icon><View /></el-icon>{{ n.viewCount || 0 }}</span>
            <span class="meta-item"><el-icon><Document /></el-icon>{{ n.wordCount || 0 }} 字</span>
          </div>
        </div>
      </div>

      <el-empty v-else-if="loaded" description="该知识库暂无公开笔记" />
      <div v-else class="loading"><el-skeleton :rows="6" animated /></div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, Clock, View, Document } from '@element-plus/icons-vue'
import { publicApi } from '@/api'
import { hlHtml, formatDate, formatDateTime } from '@/utils/format'

const route = useRoute()
const kbId = route.params.kbId
const kb = ref(null)
const notes = ref([])
const current = ref(null)   // 非空 = 阅读态
const keyword = ref('')
const loaded = ref(false)

async function loadKb() { kb.value = (await publicApi.kb(kbId)).data }
async function loadNotes() {
  const res = await publicApi.notes(kbId, keyword.value || undefined)
  notes.value = res.data; loaded.value = true
}
async function openNote(id) {
  current.value = (await publicApi.note(id)).data
  window.scrollTo({ top: 0 })
}
function backToList() { current.value = null }

onMounted(async () => {
  try { await loadKb(); await loadNotes() }
  catch (e) { window.$message?.error(e.message || '知识库不存在或未公开') }
})
</script>

<style scoped>
.share-wrap { max-width: 980px; margin: 0 auto; padding: 24px 20px; }
.head, .search { margin-bottom: 16px; }
.head h1 { margin: 0; font-size: 22px; }
.search-row { display: flex; gap: 10px; }
.muted { color: var(--c-text-sub); font-size: 13px; }

/* ============ 列表态：卡片网格 ============ */
.note-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 14px;
}
.note-card {
  display: flex; flex-direction: column; gap: 10px;
  padding: 16px 16px 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-md);
  cursor: pointer;
  min-height: 132px;
  transition: transform var(--t-base), box-shadow var(--t-base), border-color var(--t-base);
}
.note-card:hover {
  transform: translateY(-3px);
  border-color: var(--c-primary);
  box-shadow: var(--shadow-hover);
}
.note-title {
  font-family: var(--font-display);
  font-size: 16px; font-weight: 600; letter-spacing: 0.02em;
  color: var(--c-text);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.note-summary {
  flex: 1;
  font-size: 13px; line-height: 1.7; color: var(--c-text-sub);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.note-meta {
  display: flex; align-items: center; gap: 14px;
  padding-top: 10px;
  border-top: 1px solid var(--c-divider);
  color: var(--c-text-sub); font-size: 12px;
}
.meta-item { display: inline-flex; align-items: center; gap: 4px; }
.meta-item .el-icon { font-size: 13px; }
.hl :deep(em) { font-style: normal; color: var(--c-primary); }
.loading { padding: 20px; }

/* ============ 阅读态：大标题 + 正文（对齐编辑页排版） ============ */
.reader { max-width: 820px; margin: 0 auto; }
.reader-top {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 20px;
}
.reader-kb { font-size: 13px; color: var(--c-text-sub); }
.reader-title {
  margin: 6px 0 10px;
  font-family: var(--font-display);
  font-size: 28px; font-weight: 700; letter-spacing: 0.02em;
  color: var(--c-text);
  line-height: 1.4;
}
.reader-meta {
  display: flex; align-items: center; gap: 16px;
  color: var(--c-text-sub); font-size: 13px;
}
.reader-body { margin-top: 8px; font-size: 15px; line-height: 1.9; }
.rich img { max-width: 100%; }
</style>
