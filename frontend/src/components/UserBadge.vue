<template>
  <button class="badge" @click="router.push('/profile')"
          :title="(user?.nickname || user?.username || '') + '（点击进个人中心）'">
    <span class="avatar" :class="{ empty: !avatarUrl }">
      <img v-if="avatarUrl" :src="avatarUrl" alt="头像" />
      <i v-else>{{ (user?.nickname || user?.username || '?').slice(0, 1) }}</i>
    </span>
    <span class="name">{{ user?.nickname || user?.username || '' }}</span>
  </button>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/user'

/**
 * 顶栏用户信息徽标：头像 + 昵称，点击进入个人中心。
 * 头像地址为 /files/...，拼 /api 前缀经代理访问。
 */
const router = useRouter()
const store = useUserStore()
const user = ref(null)

const avatarUrl = computed(() => {
  const a = user.value?.avatar
  return a ? '/api' + a : ''
})

onMounted(async () => {
  try {
    user.value = await store.fetchInfo()
  } catch (e) {
    /* 未登录已由拦截器处理 */
  }
})
</script>

<style scoped>
.badge {
  display: inline-flex; align-items: center; gap: 8px;
  border: none; background: none; cursor: pointer; padding: 2px 4px;
  color: inherit; font-size: 14px;
}
.badge:hover .name { opacity: .8; }
.avatar {
  width: 30px; height: 30px; border-radius: 50%; overflow: hidden;
  background: #e5e7eb; display: inline-flex; align-items: center; justify-content: center;
}
.avatar i { font-style: normal; font-weight: 600; color: #4b5563; font-size: 13px; }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
</style>