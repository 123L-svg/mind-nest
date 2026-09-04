<template>
  <div class="login-wrap">
    <el-card class="login-card" shadow="never">
      <div class="brand">
        <div class="brand-logo"><el-icon :size="24"><Notebook /></el-icon></div>
        <h1 class="brand-name">AI 笔记</h1>
        <p class="brand-sub">智能笔记 · 知识库 · 随处创作</p>
      </div>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="username" size="large" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" size="large" type="password" placeholder="请输入密码"
                    show-password @keyup.enter="doLogin" />
        </el-form-item>
        <el-button class="submit" type="primary" size="large" :loading="loading" @click="doLogin">
          登录
        </el-button>
      </el-form>

      <el-divider><span class="or">其他方式登录</span></el-divider>
      <div class="oauth-row">
        <el-button class="oauth-btn" :loading="oauthing === 'github'" @click="oauthStart('github')">GitHub</el-button>
        <el-button class="oauth-btn" :loading="oauthing === 'gitee'" @click="oauthStart('gitee')">Gitee</el-button>
      </div>

      <p class="tip">联调账号：tester / 123456</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Notebook } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { authApi } from '@/api'

const router = useRouter()
const store = useUserStore()
const username = ref('tester')
const password = ref('123456')
const loading = ref(false)
const oauthing = ref('')

async function oauthStart(source) {
  if (oauthing.value) return
  oauthing.value = source
  try {
    const res = await authApi.oauthUrl(source)
    // 记录平台，供回调页回传
    sessionStorage.setItem('oauthSource', source)
    window.location.href = res.data.url
  } catch (e) {
    window.$message?.error(e.message || '第三方登录跳转失败')
    oauthing.value = ''
  }
}

async function doLogin() {
  if (!username.value || !password.value) {
    window.$message?.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await store.login({ username: username.value, password: password.value })
    window.$message?.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    window.$message?.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--c-bg);
}
.login-card {
  width: 400px;
  max-width: 100%;
  border-radius: var(--r-lg);
  border: 1px solid var(--c-border);
  box-shadow: var(--shadow-lg);
}
.login-card :deep(.el-card__body) { padding: 32px; }

.brand { text-align: center; margin-bottom: 28px; }
.brand-logo {
  width: 54px; height: 54px; margin: 0 auto 14px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 14px; background: var(--c-primary); color: #fff;
}
.brand-name { margin: 0 0 4px; font-size: 24px; font-weight: 700; color: var(--c-text); }
.brand-sub { margin: 0; font-size: 13px; color: var(--c-text-sub); }

.el-form-item { margin-bottom: 18px; }
.submit { width: 100%; margin-top: 6px; border-radius: var(--r-md); }

.oauth-row { display: flex; gap: 12px; }
.oauth-btn { flex: 1; }
.or { font-size: 12px; color: var(--c-text-sub); }

.tip { margin: 18px 0 0; font-size: 12px; color: var(--c-text-sub); text-align: center; }
</style>