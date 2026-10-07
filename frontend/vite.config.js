import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 后端 context-path 已是 /api，前端请求统一走 /api，由 Vite 代理转发到 8080，
// 避免开发期跨域。
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入：模板中的 <el-*> 自动解析对应组件与样式，显著减小主包体积
    Components({ resolvers: [ElementPlusResolver()] })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // 默认代理到本地后端；联调线上时可用 VITE_PROXY_TARGET=http://47.98.112.16 覆盖
        // （后端 CORS 仅放行正式域名，联调需同时改写 Origin 头）
        target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        headers: process.env.VITE_PROXY_TARGET
          ? { Origin: process.env.VITE_PROXY_TARGET }
          : undefined
      }
    }
  }
})