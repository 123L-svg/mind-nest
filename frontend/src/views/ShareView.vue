<template>
  <div class="share-wrap">
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

    <el-row v-if="notes.length" :gutter="16">
      <el-col :xs="24" :md="8">
        <el-card>
          <el-menu class="notes-menu">
            <el-menu-item v-for="n in notes" :key="n.id"
                          :class="{ 'is-active': current?.id === n.id }" @click="openNote(n.id)">
              <div class="note-item">
                <b>{{ n.title }}</b>
                <span v-if="n.highlight" class="hl" v-html="hlHtml(n.highlight)"></span>
                <span v-else class="muted">{{ n.summary || '' }}</span>
              </div>
            </el-menu-item>
          </el-menu>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="16">
        <el-card>
          <template v-if="current">
            <h2>{{ current.title }}</h2>
            <div class="rich" v-html="current.content"></div>
          </template>
          <el-empty v-else description="点击左侧笔记查看内容" />
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-else-if="loaded" description="该知识库暂无公开笔记" />
    <div v-else class="loading"><el-skeleton :rows="6" animated /></div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { publicApi } from '@/api'
import { hlHtml } from '@/utils/format'

const route = useRoute()
const kbId = route.params.kbId
const kb = ref(null)
const notes = ref([])
const current = ref(null)
const keyword = ref('')
const loaded = ref(false)

async function loadKb() { kb.value = (await publicApi.kb(kbId)).data }
async function loadNotes() {
  const res = await publicApi.notes(kbId, keyword.value || undefined)
  notes.value = res.data; current.value = null; loaded.value = true
}
async function openNote(id) {
  current.value = (await publicApi.note(id)).data
}

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
.notes-menu { border-right: none; }
.note-item { display: flex; flex-direction: column; gap: 4px; width: 100%; }
.muted { color: var(--c-text-sub); font-size: 13px; }
.hl { display: block; font-size: 13px; color: var(--c-text-sub); line-height: 1.5; margin-top: 2px; }
.rich { line-height: 1.7; }
.rich img { max-width: 100%; }
.loading { padding: 20px; }
</style>