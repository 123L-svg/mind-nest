import { createRouter, createWebHistory } from 'vue-router'
import { STORAGE_KEYS } from '@/constants'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
  { path: '/dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
  { path: '/note/edit', name: 'note-edit', component: () => import('@/views/NoteEditView.vue') },
  { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue') },
  { path: '/recycle', name: 'recycle', component: () => import('@/views/RecycleView.vue') },
  { path: '/stats', name: 'stats', component: () => import('@/views/StatsView.vue') },
  { path: '/tags', name: 'tags', component: () => import('@/views/TagView.vue') },
  { path: '/categories', name: 'categories', component: () => import('@/views/CategoryView.vue') },
  { path: '/oauth/callback', name: 'oauth-callback', component: () => import('@/views/OAuthCallbackView.vue') },
  { path: '/share/:kbId', name: 'share', component: () => import('@/views/ShareView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 无需登录即可访问的路由（公开分享页）
const PUBLIC_ROUTES = ['login', 'share', 'oauth-callback']

router.beforeEach((to) => {
  const token = localStorage.getItem(STORAGE_KEYS.TOKEN)
  if (!PUBLIC_ROUTES.includes(to.name) && !token) {
    return { name: 'login' }
  }
  if (to.name === 'login' && token) {
    return { name: 'dashboard' }
  }
})

export default router