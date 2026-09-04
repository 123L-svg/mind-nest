<template>
  <div class="page">
    <el-header class="bar">
      <el-button @click="back">返回</el-button>
      <div class="title">个人中心</div>
    </el-header>
    <el-main class="body">
      <el-row :gutter="16">
        <el-col :xs="24" :md="10">
          <el-card>
            <template #header><b>基本信息</b></template>
            <div class="avatar-row">
              <el-avatar :size="72" :src="avatarUrl">
                {{ initials(user) }}
              </el-avatar>
              <el-button type="primary" plain @click="pickAvatar">更换头像</el-button>
              <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onAvatarChange" />
              <p class="muted">支持 jpg/png/gif/webp，≤10MB</p>
            </div>
            <el-form label-position="top">
              <el-form-item label="用户名（不可改）">
                <el-input :model-value="user?.username" disabled />
              </el-form-item>
              <el-form-item label="昵称">
                <el-input v-model="form.nickname" placeholder="昵称" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="form.email" placeholder="邮箱" />
              </el-form-item>
              <el-button type="primary" @click="saveProfile">保存资料</el-button>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="10">
          <el-card>
            <template #header><b>修改密码</b></template>
            <el-form label-position="top">
              <el-form-item label="旧密码">
                <el-input v-model="pwd.oldPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="pwd.newPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="确认新密码">
                <el-input v-model="pwd.confirm" type="password" show-password />
              </el-form-item>
              <el-button type="primary" :loading="saving" @click="savePassword">修改密码</el-button>
            </el-form>
          </el-card>
        </el-col>
      </el-row>
    </el-main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { userApi, fileApi } from '@/api'
import { initials } from '@/utils/format'

const router = useRouter()
const store = useUserStore()
const user = ref(null)
const form = ref({ nickname: '', email: '' })
const saving = ref(false)
const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })
const fileInput = ref(null)

const avatarUrl = computed(() => {
  const a = user.value?.avatar
  return a ? '/api' + a : ''
})

function pickAvatar() { fileInput.value?.click() }

async function onAvatarChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const res = await fileApi.upload(file)
    user.value = { ...user.value, avatar: res.data.path }
    await userApi.update({ avatar: res.data.path })
    window.$message?.success('头像已更新')
    store.user = user.value
  } catch (err) {
    window.$message?.error(err.message || '头像上传失败')
  } finally { e.target.value = '' }
}

async function load() {
  user.value = await store.fetchInfo(true)
  form.value.nickname = user.value.nickname || ''
  form.value.email = user.value.email || ''
}

async function saveProfile() {
  try {
    await userApi.update({ nickname: form.value.nickname, email: form.value.email })
    window.$message?.success('保存成功')
    await load()
  } catch (e) { window.$message?.error(e.message) }
}

async function savePassword() {
  if (pwd.value.newPassword !== pwd.value.confirm) {
    return window.$message?.warning('两次输入的新密码不一致')
  }
  saving.value = true
  try {
    await userApi.password({ oldPassword: pwd.value.oldPassword, newPassword: pwd.value.newPassword })
    window.$message?.success('密码修改成功，请重新登录')
    pwd.value = { oldPassword: '', newPassword: '', confirm: '' }
  } catch (e) { window.$message?.error(e.message) } finally { saving.value = false }
}

function back() { router.push('/dashboard') }
onMounted(load)
</script>

<style scoped>
.page { min-height: 100vh; }
.bar { display: flex; align-items: center; gap: 12px; border-bottom: 1px solid var(--c-border); }
.title { flex: 1; font-weight: 600; }
.body { background: #f5f7fa; }
.avatar-row { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; flex-wrap: wrap; }
.hidden { display: none; }
.muted { color: #909399; font-size: 12px; margin-top: 4px; width: 100%; }
</style>