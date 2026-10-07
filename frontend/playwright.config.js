import { defineConfig } from '@playwright/test'

/**
 * 集成测试（E2E）配置
 * - 复用本地 Vite dev server（其 /api 代理到线上联调后端）
 * - globalSetup 统一登录一次，storageState 共享登录态，避免触发登录限流
 * - 串行执行（workers: 1），避免 AI / 登录接口限流干扰
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    storageState: '.playwright/state.json',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    locale: 'zh-CN',
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
  globalSetup: './e2e/global-setup.js',
})
