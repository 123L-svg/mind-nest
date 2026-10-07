<template>
  <div class="login-wrap">
    <!-- 左侧品牌区（窄屏隐藏） -->
    <div class="brand-pane">
      <div class="brand-pane-inner">
        <div class="pane-logo">
          <el-icon :size="26"><Notebook /></el-icon>
          <span>MindNest 智巢</span>
        </div>
        <h2 class="pane-title">让知识<br />在指尖生长</h2>
        <p class="pane-sub">智能笔记 · 知识库 · 随处创作</p>
        <ul class="pane-features">
          <li>
            <el-icon><MagicStick /></el-icon>
            <div><b>AI 智能辅助</b><span>大纲生成、润色、摘要，一键完成</span></div>
          </li>
          <li>
            <el-icon><Collection /></el-icon>
            <div><b>知识库管理</b><span>多库分类、标签体系、全文检索</span></div>
          </li>
          <li>
            <el-icon><Share /></el-icon>
            <div><b>轻松分享</b><span>公开知识库，一键复制链接</span></div>
          </li>
        </ul>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="form-pane">
      <el-card class="login-card" shadow="never">
        <div class="brand">
          <div class="brand-logo"><el-icon :size="24"><Notebook /></el-icon></div>
          <h1 class="brand-name">{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h1>
          <p class="brand-sub">{{ mode === 'login' ? '登录 MindNest，继续你的创作' : '注册 MindNest，开启你的知识之旅' }}</p>
        </div>

        <el-form v-if="mode === 'login'" label-position="top" @submit.prevent>
          <el-form-item label="用户名">
            <el-input v-model="username" size="large" placeholder="请输入用户名" clearable />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="password" size="large" type="password" placeholder="请输入密码"
                      show-password @keyup.enter="doLogin" />
          </el-form-item>
          <el-button class="submit" type="primary" size="large" :loading="loading" @click="doLogin">
            登 录
          </el-button>
        </el-form>

        <el-form v-else label-position="top" @submit.prevent>
          <el-form-item label="用户名">
            <el-input v-model="regForm.username" size="large" placeholder="3-32 位字母、数字、下划线" clearable />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="regForm.password" size="large" type="password" placeholder="6-32 位密码"
                      show-password />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="regForm.confirm" size="large" type="password" placeholder="请再次输入密码"
                      show-password @keyup.enter="doRegister" />
          </el-form-item>
          <el-form-item label="昵称（可选）">
            <el-input v-model="regForm.nickname" size="large" placeholder="不填则默认使用用户名" maxlength="64" clearable />
          </el-form-item>
          <el-button class="submit" type="primary" size="large" :loading="regLoading" @click="doRegister">
            注 册
          </el-button>
        </el-form>

        <div class="switch-mode">
          <template v-if="mode === 'login'">
            还没有账号？<el-link type="primary" :underline="false" @click="switchMode('register')">立即注册</el-link>
          </template>
          <template v-else>
            已有账号？<el-link type="primary" :underline="false" @click="switchMode('login')">返回登录</el-link>
          </template>
        </div>

        <el-divider><span class="or">其他方式登录</span></el-divider>
        <div class="oauth-row">
          <el-button class="oauth-btn" :loading="oauthing === 'github'" @click="oauthStart('github')">GitHub</el-button>
          <el-button class="oauth-btn" :loading="oauthing === 'gitee'" @click="oauthStart('gitee')">Gitee</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Notebook, MagicStick, Collection, Share } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { authApi } from '@/api'

const router = useRouter()
const store = useUserStore()
const username = ref('')
const password = ref('')
const loading = ref(false)
const oauthing = ref('')

const mode = ref('login')
const regLoading = ref(false)
const regForm = ref({ username: '', password: '', confirm: '', nickname: '' })

function switchMode(m) {
  mode.value = m
}

async function doRegister() {
  const f = regForm.value
  if (!f.username || !f.password) {
    window.$message?.warning('请输入用户名和密码')
    return
  }
  if (!/^[a-zA-Z0-9_]{3,32}$/.test(f.username)) {
    window.$message?.warning('用户名需为 3-32 位字母、数字、下划线')
    return
  }
  if (f.password.length < 6 || f.password.length > 32) {
    window.$message?.warning('密码长度需在 6-32 位之间')
    return
  }
  if (f.password !== f.confirm) {
    window.$message?.warning('两次输入的密码不一致')
    return
  }
  regLoading.value = true
  try {
    await authApi.register({
      username: f.username,
      password: f.password,
      nickname: f.nickname || undefined
    })
    window.$message?.success('注册成功，请登录')
    // 回填用户名并切回登录
    username.value = f.username
    password.value = ''
    mode.value = 'login'
  } catch (e) {
    window.$message?.error(e.message || '注册失败')
  } finally {
    regLoading.value = false
  }
}

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
}

/* 左侧品牌区 */
.brand-pane {
  flex: 1.1;
  display: flex; align-items: center; justify-content: center;
  background: var(--grad-brand);
  position: relative;
  overflow: hidden;
}
/* 装饰光斑 */
.brand-pane::before,
.brand-pane::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}
.brand-pane::before { width: 320px; height: 320px; top: -80px; left: -100px; }
.brand-pane::after { width: 240px; height: 240px; bottom: -60px; right: -70px; }

.brand-pane-inner { position: relative; color: #fff; max-width: 400px; padding: 40px; }
.pane-logo {
  display: inline-flex; align-items: center; gap: 8px;
  font-size: 18px; font-weight: 700;
  padding: 8px 16px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(4px);
}
.pane-title { margin: 28px 0 10px; font-size: 40px; line-height: 1.25; font-weight: 700; }
.pane-sub { margin: 0 0 36px; font-size: 15px; opacity: 0.9; }

.pane-features { list-style: none; display: flex; flex-direction: column; gap: 20px; }
.pane-features li { display: flex; gap: 12px; align-items: flex-start; }
.pane-features .el-icon {
  flex-shrink: 0;
  width: 38px; height: 38px;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.18);
  font-size: 17px;
}
.pane-features b { display: block; font-size: 15px; margin-bottom: 2px; }
.pane-features span { font-size: 13px; opacity: 0.85; }

/* 右侧表单区 */
.form-pane {
  flex: 1;
  display: flex; align-items: center; justify-content: center;
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
.login-card :deep(.el-card__body) { padding: 36px 32px; }

.brand { text-align: center; margin-bottom: 26px; }
.brand-logo {
  width: 54px; height: 54px; margin: 0 auto 14px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 14px; background: var(--grad-brand); color: #fff;
}
.brand-name { margin: 0 0 6px; font-size: 22px; font-weight: 700; color: var(--c-text); }
.brand-sub { margin: 0; font-size: 13px; color: var(--c-text-sub); }

.el-form-item { margin-bottom: 18px; }
.submit {
  width: 100%; margin-top: 6px;
  border-radius: var(--r-md);
  background: var(--grad-brand);
  border: none;
  letter-spacing: 4px;
}
.submit:hover { opacity: 0.9; }

.oauth-row { display: flex; gap: 12px; }
.oauth-btn { flex: 1; border-radius: var(--r-md); }
.or { font-size: 12px; color: var(--c-text-sub); }

.switch-mode {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--c-text-sub);
}
.switch-mode .el-link { font-size: 13px; vertical-align: baseline; }

/* 窄屏：隐藏品牌区，仅保留表单 */
@media (max-width: 860px) {
  .brand-pane { display: none; }
  .form-pane { flex: 1; }
}
</style>
