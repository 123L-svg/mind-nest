import { test, expect } from '@playwright/test'

/**
 * AI 对话集成测试（走异步 MQ + 轮询链路，当前对接真实 gpt-4o-mini）
 */
const KB_ID = '210633393522144130'

test('AI 对话：提问后出现用户与 AI 气泡', async ({ page }) => {
  await page.goto(`/note/edit?kbId=${KB_ID}`)

  // 先输入笔记内容（chat 需有内容才可用）
  const editor = page.locator('.w-e-text-container [contenteditable="true"]')
  await expect(editor).toBeVisible()
  await editor.click()
  await page.keyboard.type('人工智能正在改变笔记类产品的交互方式。')

  // 空状态提示可见
  await expect(page.getByText('我是你的 AI 写作助手')).toBeVisible()

  // 输入问题并发送（Enter 发送）
  const chatInput = page.locator('.chat-input textarea')
  await expect(chatInput).toBeEnabled()
  await chatInput.fill('用一句话概括这篇笔记')
  await page.keyboard.press('Enter')

  // 用户气泡立即出现
  await expect(page.locator('.chat-msg.user')).toBeVisible()

  // AI 气泡最终出现且非 pending（真实模型经 MQ 轮询，最长约 60s）
  const aiBubble = page.locator('.chat-msg.ai .chat-bubble')
  await expect(aiBubble).toBeVisible({ timeout: 75_000 })
  await expect(aiBubble.locator('pre')).not.toContainText('AI 处理中', { timeout: 75_000 })
  const text = await aiBubble.innerText()
  expect(text.trim().length, 'AI 回复非空且非演示文案').toBeGreaterThan(4)
  expect(text).not.toContain('演示模式')
})
