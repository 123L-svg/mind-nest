<template>
  <div class="page">
    <el-header class="bar">
      <el-button @click="back">返回</el-button>
      <div class="title">回收站</div>
      <el-button @click="load">刷新</el-button>
    </el-header>
    <el-main class="body">
      <el-card>
        <el-table :data="list" empty-text="回收站为空">
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column label="删除于" width="200">
            <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="词数" width="90" align="center">
            <template #default="{ row }">{{ row.wordCount }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="restore(row.id)">恢复</el-button>
              <el-button link type="danger" @click="purge(row.id)">永久删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { noteApi } from '@/api'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const list = ref([])

async function load() {
  const res = await noteApi.recycle({ page: 1, size: 100 })
  list.value = res.data.records
}

async function restore(id) {
  await noteApi.restore(id)
  window.$message?.success('已恢复')
  await load()
}

async function purge(id) {
  try { await window.$confirm('永久删除后不可恢复，确认？', '提示', { type: 'warning' }) }
  catch { return }
  await noteApi.purge(id)
  window.$message?.success('已删除')
  await load()
}

function back() { router.push('/dashboard') }
onMounted(load)
</script>

<style scoped>
.page { min-height: 100vh; }
.bar { display: flex; align-items: center; gap: 12px; border-bottom: 1px solid var(--c-border); background: var(--c-surface); }
.title { flex: 1; font-weight: 600; }
.body { background: var(--c-bg); }
</style>