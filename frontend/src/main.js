import { createApp } from 'vue'
import App from '@/App.vue'
import router from '@/router'
import { createPinia } from 'pinia'
import '@/styles/index.css'
import { useTheme } from '@/composables/useTheme'

// 函数式消息组件（Element Plus 按需模式下样式需手动引入）
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
window.$message = ElMessage
window.$confirm = ElMessageBox.confirm

// 主题初始化：从本地存储恢复明/暗模式（切换交互见 useTheme）
useTheme().init()

const app = createApp(App)
app.use(router)
app.use(createPinia())
app.mount('#app')