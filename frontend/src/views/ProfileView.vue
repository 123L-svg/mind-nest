<template>
  <div class="page page-shell">
    <PageHeader title="个人中心" subtitle="管理你的账户资料与登录安全">
      <el-tag v-if="user?.oauthType" size="small" type="success" effect="plain">
        {{ oauthName }} 账号
      </el-tag>
    </PageHeader>

    <main class="page-container">
      <el-row :gutter="16">
        <el-col :xs="24" :md="14">
          <el-card>
            <template #header><b>基本信息</b></template>
            <div class="avatar-card">
              <div class="avatar-ring">
                <el-avatar :size="76" :src="avatarUrl">{{ initials(user) }}</el-avatar>
              </div>
              <div class="avatar-actions">
                <el-button type="primary" plain size="small" @click="pickAvatar">更换头像</el-button>
                <p class="muted">支持 jpg/png/gif/webp，≤10MB</p>
              </div>
              <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onAvatarChange" />
            </div>
            <el-form label-position="top">
              <el-form-item label="用户名（不可修改）">
                <el-input :model-value="user?.username" disabled />
              </el-form-item>
              <el-form-item label="昵称">
                <el-input v-model="form.nickname" placeholder="给自己起个昵称" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="form.email" placeholder="用于接收通知（可选）" />
              </el-form-item>
              <el-button type="primary" @click="saveProfile">保存资料</el-button>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="10">
          <el-card class="pwd-card">
            <template #header><b>修改密码</b></template>
            <el-alert v-if="user?.oauthType" type="info" :closable="false" class="oauth-tip"
                      title="第三方登录账号无本地密码，如需密码请联系管理员或使用账号注册" />
            <el-form label-position="top">
              <el-form-item label="旧密码">
                <el-input v-model="pwd.oldPassword" type="password" show-password placeholder="当前密码" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="pwd.newPassword" type="password" show-password placeholder="不少于 6 位" />
              </el-form-item>
              <el-form-item label="确认新密码">
                <el-input v-model="pwd.confirm" type="password" show-password placeholder="再次输入新密码" />
              </el-form-item>
              <el-button type="primary" :loading="saving" @click="savePassword">修改密码</el-button>
            </el-form>
          </el-card>
        </el-col>
      </el-row>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import PageHeader from '@/components/PageHeader.vue'
import { userApi, fileApi } from '@/api'
import { initials } from '@/utils/format'

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

// 第三方登录来源展示（github/gitee）
const oauthName = computed(() => {
  const t = user.value?.oauthType
  if (t === 'github') return 'GitHub'
  if (t === 'gitee') return 'Gitee'
  return t || '第三方'
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

onMounted(load)
</script>

<style scoped>
.page { min-height: 100vh; }

/* 头像区：渐变环 + 操作列 */
.avatar-card {
  display: flex; align-items: center; gap: 18px;
  margin-bottom: 22px;
}
.avatar-ring {
  flex-shrink: 0;
  padding: 3px;
  border-radius: 50%;
  background: var(--grad-brand);
}
.avatar-ring :deep(.el-avatar) {
  border: 2px solid var(--c-surface);
}
.avatar-actions { display: flex; flex-direction: column; gap: 6px; }
.avatar-actions .muted { margin: 0; }

.hidden { display: none; }

/* 修改密码卡片顶部与左侧卡片对齐 */
.oauth-tip { margin-bottom: 16px; }

@media (max-width: 768px) {
  .pwd-card { margin-top: 16px; }
}
</style>