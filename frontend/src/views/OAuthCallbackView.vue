<template>
  <div class="cb-wrap">
    <el-card class="cb-card">
      <template #header><div class="title">第三方登录</div></template>
      <div v-if="loading" class="center">授权处理中…</div>
      <el-result v-else-if="error" icon="error" :title="error">
        <template #extra>
          <el-button type="primary" @click="router.push('/login')">返回登录</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authApi } from '@/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  const code = route.query.code
  const state = route.query.state
  const source = sessionStorage.getItem('oauthSource')
  if (!code || !state || !source) {
    error.value = '第三方授权信息缺失，请重新登录'
    loading.value = false
    return
  }
  try {
    const res = await authApi.oauthLogin({ source, code, state })
    store.saveToken(res.data.token)
    sessionStorage.removeItem('oauthSource')
    window.$message?.success('第三方登录成功')
    router.replace('/dashboard')
  } catch (e) {
    sessionStorage.removeItem('oauthSource')
    error.value = e.message || '第三方登录失败'
    loading.value = false
  }
})
</script>

<style scoped>
.cb-wrap { display: flex; justify-content: center; align-items: center; min-height: 100vh; }
.cb-card { width: 360px; }
.title { font-weight: 600; }
.center { padding: 24px; text-align: center; color: var(--c-text-sub); }
</style>