<template>
  <div class="login-wrap">
    <!-- 左侧品牌区（窄屏隐藏） -->
    <div class="brand-pane">
      <span class="watermark" aria-hidden="true">巢</span>
      <p class="vertical-line" aria-hidden="true">以纸为巢 · 以墨为息</p>
      <div class="brand-pane-inner">
        <div class="pane-logo">
          <span class="seal">巢</span>
          <span class="logo-word">MindNest · 智巢</span>
        </div>
        <p class="pane-kicker">WHERE IDEAS GROW</p>
        <h2 class="pane-title">让知识<br />在<em>指尖</em>生长</h2>
        <p class="pane-sub">智能笔记 · 知识库 · 随处创作</p>
        <ol class="pane-features">
          <li>
            <i class="num">壹</i>
            <div><b>AI 智能辅助</b><span>大纲生成、润色、摘要，一键完成</span></div>
          </li>
          <li>
            <i class="num">贰</i>
            <div><b>知识库管理</b><span>多库分类、标签体系、全文检索</span></div>
          </li>
          <li>
            <i class="num">叁</i>
            <div><b>轻松分享</b><span>公开知识库，一键复制链接</span></div>
          </li>
        </ol>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="form-pane">
      <el-card class="login-card" shadow="never">
        <div class="brand">
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
            登录
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
            注册
          </el-button>
        </el-form>

        <div class="switch-mode">
          <template v-if="mode === 'login'">
            还没有账号？<el-link :underline="false" @click="switchMode('register')">立即注册</el-link>
          </template>
          <template v-else>
            已有账号？<el-link :underline="false" @click="switchMode('login')">返回登录</el-link>
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
/* ============ 绢帛鎏金（仅本页生效，不污染全局令牌） ============ */
.login-wrap {
  /* 绢色系 */
  --silk: #ece2c9;         /* 品牌区绢底（暖米黄） */
  --silk-2: #e4d8ba;       /* 表单区绢底（略深，做分区） */
  --panel: #f8f1de;        /* 卡片宣纸面 */
  --ink: #33291a;          /* 褐墨主文字 */
  --ink-sub: #8a7a58;     /* 弱化文字 */
  --gold: #a1772a;         /* 赭金 */
  --gold-deep: #8a651f;    /* 深金（hover） */
  --line: rgba(140, 110, 50, 0.30);      /* 金褐细线 */
  --seal: #b03a2a;         /* 朱砂 */

  --font-display: 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  --font-body: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;

  min-height: 100vh;
  display: flex;
  background: var(--silk);
  color: var(--ink);
  font-family: var(--font-body);
}

/* ---------- 左侧品牌区 ---------- */
.brand-pane {
  flex: 1.1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background:
    radial-gradient(ellipse 70% 55% at 72% 18%, rgba(255, 251, 235, 0.55), transparent 62%),
    radial-gradient(ellipse 55% 45% at 10% 94%, rgba(176, 58, 42, 0.05), transparent 60%),
    var(--silk);
  border-right: 1px solid var(--line);
}
/* 绢面颗粒 */
.brand-pane::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: .55;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2'/%3E%3CfeColorMatrix values='0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 0.035 0'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)'/%3E%3C/svg%3E");
}
/* 巨型描边水印 */
.watermark {
  position: absolute;
  right: -4%;
  bottom: -16%;
  font-family: var(--font-display);
  font-size: 400px;
  line-height: 1;
  font-weight: 700;
  color: transparent;
  -webkit-text-stroke: 1px rgba(120, 90, 30, 0.13);
  user-select: none;
  pointer-events: none;
}
/* 竖排诗句 */
.vertical-line {
  position: absolute;
  top: 60px;
  right: 64px;
  margin: 0;
  writing-mode: vertical-rl;
  font-family: var(--font-display);
  font-size: 13px;
  letter-spacing: 0.55em;
  color: var(--gold);
}
.vertical-line::before {
  content: '';
  display: block;
  width: 1px;
  height: 56px;
  margin: 0 auto 18px;
  background: linear-gradient(to bottom, transparent, var(--gold));
  opacity: .7;
}

.brand-pane-inner {
  position: relative;
  max-width: 400px;
  padding: 48px 44px;
}

/* 朱砂印章 */
.pane-logo {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}
.seal {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  color: #f7ecd7;
  background: var(--seal);
  border-radius: 3px;
  box-shadow: inset 0 0 0 2px rgba(247, 236, 215, 0.25), 0 2px 10px rgba(176, 58, 42, 0.28);
}
.logo-word {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.1em;
  color: var(--ink-sub);
}

.pane-kicker {
  margin: 52px 0 10px;
  font-size: 11px;
  letter-spacing: 0.42em;
  color: var(--gold);
}
.pane-title {
  margin: 0 0 16px;
  font-family: var(--font-display);
  font-size: 48px;
  line-height: 1.28;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--ink);
}
.pane-title em {
  font-style: normal;
  color: var(--gold);
}
.pane-sub {
  margin: 0 0 52px;
  font-size: 14px;
  letter-spacing: 0.22em;
  color: var(--ink-sub);
}

.pane-features {
  list-style: none;
  margin: 0;
  padding: 0;
}
.pane-features li {
  display: flex;
  gap: 18px;
  align-items: baseline;
  padding: 18px 0;
  border-top: 1px solid var(--line);
}
.pane-features li:last-child {
  border-bottom: 1px solid var(--line);
}
.pane-features .num {
  font-style: normal;
  font-family: var(--font-display);
  font-size: 14px;
  color: var(--gold);
  flex-shrink: 0;
}
.pane-features b {
  display: block;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 3px;
  color: var(--ink);
}
.pane-features span {
  font-size: 13px;
  color: var(--ink-sub);
}

/* ---------- 右侧表单区 ---------- */
.form-pane {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--silk-2);
}

.login-card {
  width: 400px;
  max-width: 100%;
  border: 1px solid var(--line);
  border-top: 2px solid var(--gold);
  border-radius: 3px;
  background: var(--panel);
  box-shadow: 0 1px 2px rgba(80, 60, 20, 0.06), 0 20px 50px rgba(80, 60, 20, 0.13);
}
.login-card :deep(.el-card__body) {
  padding: 44px 40px 36px;
}

.brand {
  margin-bottom: 30px;
}
.brand-name {
  margin: 0 0 8px;
  font-family: var(--font-display);
  font-size: 27px;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--ink);
}
.brand-sub {
  margin: 0;
  font-size: 13px;
  color: var(--ink-sub);
}
.brand-sub::before {
  content: '';
  display: inline-block;
  width: 20px;
  height: 1px;
  margin: 4px 10px 3px 0;
  vertical-align: middle;
  background: var(--gold);
  opacity: .8;
}

/* 表单项：宣纸输入框 */
.login-card :deep(.el-form-item) {
  margin-bottom: 20px;
}
.login-card :deep(.el-form-item__label) {
  font-size: 12px;
  color: var(--gold);
  letter-spacing: 0.14em;
  padding-bottom: 6px;
}
.login-card :deep(.el-input__wrapper) {
  background: rgba(255, 253, 245, 0.75);
  border-radius: 3px;
  box-shadow: 0 0 0 1px var(--line) inset;
  transition: box-shadow 0.2s ease, background 0.2s ease;
}
.login-card :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(140, 110, 50, 0.55) inset;
}
.login-card :deep(.el-input__wrapper.is-focus) {
  background: #fffdf6;
  box-shadow: 0 0 0 1px var(--gold) inset;
}
.login-card :deep(.el-input__inner) {
  color: var(--ink);
}
.login-card :deep(.el-input__inner::placeholder) {
  color: rgba(138, 122, 88, 0.6);
}
.login-card :deep(.el-input__suffix .el-icon) {
  color: var(--ink-sub);
}

/* 赭金主按钮 */
.submit {
  width: 100%;
  margin-top: 8px;
  height: 44px;
  border: none;
  border-radius: 3px;
  background: var(--gold);
  color: #fdf6e3;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.3em;
  text-indent: 0.3em;
  transition: background 0.2s ease, transform 0.15s ease, box-shadow 0.2s ease;
}
.submit:hover,
.submit:focus {
  background: var(--gold-deep);
  color: #fdf6e3;
  box-shadow: 0 4px 18px rgba(161, 119, 42, 0.3);
}
.submit:active {
  transform: translateY(1px);
}

.switch-mode {
  margin-top: 18px;
  text-align: center;
  font-size: 13px;
  color: var(--ink-sub);
}
.switch-mode :deep(.el-link) {
  font-size: 13px;
  color: var(--gold);
}
.switch-mode :deep(.el-link:hover) {
  color: var(--gold-deep);
}

.login-card :deep(.el-divider) {
  margin: 26px 0 20px;
  border-top-color: var(--line);
}
.or {
  font-size: 12px;
  color: var(--ink-sub);
  letter-spacing: 0.12em;
  background: var(--panel);
  padding: 0 6px;
}

.oauth-row {
  display: flex;
  gap: 12px;
}
.oauth-btn {
  flex: 1;
  height: 40px;
  border-radius: 3px;
  background: transparent;
  border: 1px solid var(--line);
  color: var(--ink-sub);
  font-size: 13px;
  letter-spacing: 0.06em;
  transition: border-color 0.2s ease, color 0.2s ease;
}
.oauth-btn:hover,
.oauth-btn:focus {
  border-color: var(--gold);
  color: var(--gold);
  background: transparent;
}

/* ---------- 入场动效 ---------- */
@keyframes rise {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: none; }
}
/* 印章落款：盖章动效 */
@keyframes stamp {
  0% { opacity: 0; transform: scale(1.9) rotate(-9deg); }
  62% { opacity: 1; transform: scale(0.94) rotate(2deg); }
  100% { opacity: 1; transform: scale(1) rotate(0); }
}
.brand-pane-inner > * {
  animation: rise 0.55s ease backwards;
}
.brand-pane-inner .pane-logo {
  animation: none;
}
.pane-logo .seal {
  animation: stamp 0.5s 0.35s cubic-bezier(.2, .8, .3, 1.25) backwards;
}
.pane-logo .logo-word {
  animation: rise 0.5s 0.15s ease backwards;
}
.brand-pane-inner > :nth-child(2) { animation-delay: 0.18s; }
.brand-pane-inner > :nth-child(3) { animation-delay: 0.26s; }
.brand-pane-inner > :nth-child(4) { animation-delay: 0.34s; }
.brand-pane-inner > :nth-child(5) { animation-delay: 0.42s; }
.vertical-line {
  animation: rise 0.8s 0.5s ease backwards;
}
.login-card {
  animation: rise 0.6s 0.2s ease backwards;
}
@media (prefers-reduced-motion: reduce) {
  .brand-pane-inner > *,
  .brand-pane-inner .seal,
  .brand-pane-inner .logo-word,
  .vertical-line,
  .login-card {
    animation: none;
  }
}

/* ---------- 窄屏 ---------- */
@media (max-width: 860px) {
  .brand-pane { display: none; }
  .form-pane { flex: 1; }
  .login-card :deep(.el-card__body) { padding: 36px 28px 30px; }
}
</style>
