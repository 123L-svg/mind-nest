<template>
  <div class="page">
    <el-header class="bar">
      <el-button @click="back">返回</el-button>
      <div class="title">标签管理</div>
    </el-header>
    <el-main class="body">
      <el-card>
        <template #header><b>添加标签</b></template>
        <div class="add-row">
          <el-input v-model="name" placeholder="新标签名称（≤32 字符）" maxlength="32"
                    clearable @keyup.enter="add" />
          <el-button type="primary" :loading="saving" @click="add">添加</el-button>
        </div>
      </el-card>

      <el-card style="margin-top:16px">
        <template #header><b>我的标签（{{ list.length }}）</b></template>
        <div v-if="list.length" class="tags">
          <el-tag v-for="t in list" :key="t.id" closable @close="remove(t.id)" :disable-transitions="true">
            {{ t.name }}
          </el-tag>
        </div>
        <el-empty v-else description="暂无标签" :image-size="80" />
      </el-card>
    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { tagApi } from '@/api'

const router = useRouter()
const list = ref([])
const name = ref('')
const saving = ref(false)

async function load() { list.value = (await tagApi.list()).data }

async function add() {
  const n = name.value.trim()
  if (!n) return window.$message?.warning('请输入标签名称')
  if (n.length > 32) return window.$message?.warning('标签名称不能超过 32 字符')
  saving.value = true
  try {
    await tagApi.add({ name: n })
    name.value = ''
    window.$message?.success('添加成功')
    await load()
  } catch (e) { window.$message?.error(e.message) } finally { saving.value = false }
}

async function remove(id) {
  try { await window.$confirm('删除该标签？', '提示', { type: 'warning' }) } catch { return }
  await tagApi.remove(id)
  await load()
}

function back() { router.push('/dashboard') }
onMounted(load)
</script>

<style scoped>
.page { min-height: 100vh; }
.bar { display: flex; align-items: center; gap: 12px; border-bottom: 1px solid var(--c-border); }
.title { flex: 1; font-weight: 600; }
.body { background: #f5f7fa; }
.add-row { display: flex; gap: 10px; }
.tags { display: flex; flex-wrap: wrap; gap: 8px; }
</style>