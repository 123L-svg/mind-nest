import { defineStore } from 'pinia'
import { authApi, userApi } from '@/api'
import { STORAGE_KEYS } from '@/constants'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(STORAGE_KEYS.TOKEN) || '',
    user: null
  }),
  actions: {
    async login(payload) {
      const res = await authApi.login(payload)
      this.token = res.data.token
      localStorage.setItem(STORAGE_KEYS.TOKEN, this.token)
      return res
    },
    // 第三方登录 / 通用方式保存令牌（token 已在后端签发）
    saveToken(token) {
      this.token = token
      localStorage.setItem(STORAGE_KEYS.TOKEN, token)
    },
    async fetchInfo(force = false) {
      if (this.user && !force) return this.user
      const res = await userApi.info()
      this.user = res.data
      return res.data
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(STORAGE_KEYS.TOKEN)
    }
  }
})