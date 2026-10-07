import { test, expect } from '@playwright/test'

/**
 * 登录流程集成测试（使用独立上下文，不携带 storageState）
 */
test.use({ storageState: { cookies: [], origins: [] } })

test('账号密码登录成功进入仪表盘', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByPlaceholder(/用户名|账号/)).toBeVisible()

  await page.getByPlaceholder(/用户名|账号/).fill('admin3')
  await page.getByPlaceholder(/密码/).fill('123456')
  await page.getByRole('button', { name: /登\s*录/ }).click()

  // 登录成功跳转仪表盘
  await expect(page).toHaveURL(/dashboard/)
  await expect(page.getByText('知识库管理', { exact: true })).toBeVisible({ timeout: 15_000 })

  // token 已写入
  const token = await page.evaluate(() => localStorage.getItem('token'))
  expect(token).toBeTruthy()
})

test('错误密码被拒绝且不跳转', async ({ page }) => {
  await page.goto('/login')
  await page.getByPlaceholder(/用户名|账号/).fill('admin3')
  await page.getByPlaceholder(/密码/).fill('wrong-password')
  await page.getByRole('button', { name: /登\s*录/ }).click()

  // 停留在登录页
  await page.waitForTimeout(2000)
  await expect(page).toHaveURL(/login/)
})
