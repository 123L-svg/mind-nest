import axios from 'axios'
import router from '@/router'
import { STORAGE_KEYS } from '@/constants'

// 后端 context-path=/api，前端可直接复用 /api 前缀（由 Vite 代理转发到 8080）
const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

const clearAuth = () => localStorage.removeItem(STORAGE_KEYS.TOKEN)

// 请求拦截：注入 token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem(STORAGE_KEYS.TOKEN)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：剥壳统一返回 { code, message, data }
http.interceptors.response.use(
  (res) => {
    const body = res.data
    // 后端以 HTTP 200 包装业务状态码：code=401（未登录/过期）时清理并跳登录
    if (body && body.code === 401) {
      clearAuth()
      router.push('/login')
    }
    if (body && body.code === 200) {
      return body
    }
    // 后端业务异常（600）等
    return Promise.reject(new Error(body?.message || '请求失败'))
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      clearAuth()
      router.push('/login')
    }
    return Promise.reject(
      new Error(err?.response?.data?.message || err.message || '网络错误')
    )
  }
)

export default http