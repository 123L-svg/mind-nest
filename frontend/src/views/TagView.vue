<template>
  <div class="page page-shell">
    <PageHeader title="标签管理" subtitle="标签可跨知识库复用，帮助你快速归类与检索" />

    <main class="page-container">
      <el-card>
        <template #header><b>添加标签</b></template>
        <div class="add-row">
          <el-input v-model="name" placeholder="新标签名称（≤32 字符）" maxlength="32"
                    clearable @keyup.enter="add" />
          <el-button type="primary" :loading="saving" @click="add">添加</el-button>
        </div>
      </el-card>

      <el-card class="tags-card">
        <template #header><b>我的标签（{{ list.length }}）</b></template>
        <div v-if="list.length" class="tags">
          <el-tag v-for="t in list" :key="t.id" closable size="large" effect="plain" round
                  class="tag-chip" @close="remove(t.id)" :disable-transitions="true">
            # {{ t.name }}
          </el-tag>
        </div>
        <el-empty v-else description="还没有标签，先在上方添加一个吧" :image-size="90" />
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { tagApi } from '@/api'

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

onMounted(load)
</script>

<style scoped>
.page { min-height: 100vh; }
.add-row { display: flex; gap: 10px; }
.add-row .el-input { flex: 1; }
.tags-card { margin-top: 16px; }
.tags { display: flex; flex-wrap: wrap; gap: 10px; }
/* 标签胶囊：悬浮上浮 + 主色描边 */
.tag-chip {
  cursor: default;
  transition: transform var(--t-fast), border-color var(--t-fast);
}
.tag-chip:hover {
  transform: translateY(-2px);
  border-color: var(--c-primary);
  color: var(--c-primary);
}
</style>