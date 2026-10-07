<template>
  <div class="page page-shell">
    <PageHeader title="回收站" subtitle="删除的笔记会保留在此，可恢复或永久删除">
      <el-button size="small" @click="load">刷新</el-button>
    </PageHeader>

    <main class="page-container">
      <el-card>
        <el-table :data="list" empty-text="回收站是空的，很干净">
          <el-table-column prop="title" label="标题" min-width="180">
            <template #default="{ row }">{{ row.title || '（无标题）' }}</template>
          </el-table-column>
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
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { noteApi } from '@/api'
import { formatDateTime } from '@/utils/format'

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

onMounted(load)
</script>

<style scoped>
.page { min-height: 100vh; }
</style>