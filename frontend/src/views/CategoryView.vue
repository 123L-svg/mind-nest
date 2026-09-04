<template>
  <div class="page">
    <el-header class="bar">
      <el-button @click="back">返回</el-button>
      <div class="title">分类管理</div>
    </el-header>
    <el-main class="body">
      <el-card>
        <template #header><b>选择知识库</b></template>
        <el-select v-model="kbId" placeholder="请选择知识库" style="width:100%" @change="onKbChange">
          <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
      </el-card>

      <el-card v-if="kbId" style="margin-top:16px">
        <template #header><b>分类管理</b></template>
        <div class="add-row">
          <el-input v-model="editName" placeholder="分类名称（≤64 字符）" maxlength="64"
                    clearable @keyup.enter="submit" />
          <el-button type="primary" :loading="saving" @click="submit">
            {{ editingId ? '保存修改' : '新增分类' }}
          </el-button>
          <el-button v-if="editingId" @click="cancelEdit">取消</el-button>
        </div>
        <el-table :data="list" empty-text="该知识库暂无分类" style="margin-top:12px">
          <el-table-column prop="name" label="分类名称" min-width="160" />
          <el-table-column label="排序" width="90" align="center">
            <template #default="{ row }">{{ row.sort }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="startEdit(row)">改</el-button>
              <el-button link type="danger" @click="remove(row.id)">删</el-button>
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
import { kbApi, categoryApi } from '@/api'

const router = useRouter()
const kbs = ref([])
const kbId = ref('')
const list = ref([])
const editName = ref('')
const editingId = ref(null)
const saving = ref(false)

async function loadKbs() { kbs.value = (await kbApi.list()).data }
async function load() {
  if (!kbId.value) return
  list.value = (await categoryApi.list(kbId.value)).data
}
function onKbChange() { editingId.value = null; editName.value = ''; load() }

function startEdit(c) { editingId.value = c.id; editName.value = c.name }
function cancelEdit() { editingId.value = null; editName.value = '' }

async function submit() {
  const n = editName.value.trim()
  if (!n) return window.$message?.warning('请输入分类名称')
  saving.value = true
  try {
    if (editingId.value) { await categoryApi.update({ id: editingId.value, kbId: kbId.value, name: n }) }
    else { await categoryApi.add({ kbId: kbId.value, name: n }) }
    editName.value = ''; editingId.value = null
    window.$message?.success('保存成功')
    await load()
  } catch (e) { window.$message?.error(e.message) } finally { saving.value = false }
}

async function remove(id) {
  try { await window.$confirm('删除该分类？', '提示', { type: 'warning' }) } catch { return }
  await categoryApi.remove(id)
  if (editingId.value === id) cancelEdit()
  await load()
}

function back() { router.push('/dashboard') }
onMounted(loadKbs)
</script>

<style scoped>
.page { min-height: 100vh; }
.bar { display: flex; align-items: center; gap: 12px; border-bottom: 1px solid var(--c-border); }
.title { flex: 1; font-weight: 600; }
.body { background: #f5f7fa; }
.add-row { display: flex; gap: 10px; }
</style>