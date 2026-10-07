import { request } from '@playwright/test'
import fs from 'node:fs'

/**
 * 全局前置：API 登录一次，写入 storageState 供所有用例复用（避免重复登录触发限流）
 */
export default async function globalSetup() {
  const ctx = await request.newContext({ baseURL: 'http://localhost:5173' })
  const res = await ctx.post('/api/auth/login', {
    data: { username: 'admin3', password: '123456' },
  })
  const body = await res.json()
  if (body.code !== 200 || !body.data?.token) {
    throw new Error('globalSetup 登录失败：' + JSON.stringify(body))
  }
  const token = body.data.token
  fs.mkdirSync('.playwright', { recursive: true })
  fs.writeFileSync(
    '.playwright/state.json',
    JSON.stringify({
      origins: [
        {
          origin: 'http://localhost:5173',
          localStorage: [{ name: 'token', value: token }],
        },
      ],
    })
  )
  // 供用例内清理数据使用
  fs.writeFileSync('.playwright/token.txt', token)
  await ctx.dispose()
}
