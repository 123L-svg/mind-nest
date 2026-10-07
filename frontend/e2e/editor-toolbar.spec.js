import { test, expect } from '@playwright/test'

/**
 * 编辑器工具栏集成测试
 * 覆盖：标题样式、加粗/斜体/下划线、颜色/高亮、字号/字体/行高、
 *       无序/有序列表、对齐、引用、表情、链接、表格、代码块、分割线、
 *       撤销/重做、全屏、保存与刷新后持久化
 * 运行后自动清理测试笔记（删除并从回收站彻底清除）
 *
 * DOM 结构（wangEditor v5）：
 *   .w-e-bar-item > button[data-menu-key=...] + 面板（.w-e-select-list / .w-e-drop-panel / .w-e-modal）
 *   即 data-menu-key 在 button 上，面板渲染在其外层 bar-item 内部。
 *   注意：未选中文字时格式类按钮为 disabled，点击无效果——用例中先选区再点按钮。
 */
const KB_ID = '210633393522144130'
const NOTE_TITLE = `E2E工具栏测试-${Date.now()}`

/** 菜单项容器（含 button 与面板） */
const barItem = (page, menuKey) =>
  page.locator(`.w-e-bar-item:has([data-menu-key="${menuKey}"])`).first()

/**
 * 点击工具栏按钮。
 * 关键点：wangEditor 的 changeMenuState（disabled 状态刷新）有防抖——
 * 输入/选区变化后立即点击会命中尚未刷新的 disabled 缓存而被吞掉。
 * 因此先轮询等待按钮脱离 disabled，再用 JS 派发 click
 * （无鼠标移动，避免 tooltip/弹出层遮挡 pointer 事件；分组内隐藏按钮同样可点）。
 */
async function clickMenu(page, menuKey) {
  await page.evaluate(
    (key) => {
      const el = document.querySelector(`.w-e-toolbar [data-menu-key="${key}"]`)
      if (!el) throw new Error('toolbar menu not found: ' + key)
      return new Promise((resolve, reject) => {
        const t0 = Date.now()
        const check = () => {
          if (!el.classList.contains('disabled')) {
            el.click()
            resolve()
          } else if (Date.now() - t0 > 4000) {
            reject(new Error('按钮持续 disabled: ' + key))
          } else {
            setTimeout(check, 100)
          }
        }
        check()
      })
    },
    menuKey
  )
}

/** 打开工具栏菜单面板：点击按钮并在 bar-item 内等待面板元素（带重试防 toggle 竞态） */
async function openMenu(page, menuKey, panelSel = 'li') {
  const item = barItem(page, menuKey)
  for (let i = 0; i < 4; i++) {
    await clickMenu(page, menuKey)
    try {
      await item.locator(panelSel).first().waitFor({ state: 'visible', timeout: 1200 })
      return item
    } catch {
      /* 面板未开，重试 */
    }
  }
  throw new Error(`菜单面板未能打开: ${menuKey}`)
}

test.describe.serial('编辑器工具栏', () => {
  let noteId = null
  let page

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage()
  })

  test.afterAll(async () => {
    // 清理：删除测试笔记并从回收站彻底清除（复用页面登录态）
    if (noteId && page) {
      await page.evaluate(async (id) => {
        const token = localStorage.getItem('token')
        const opt = { headers: { Authorization: `Bearer ${token}` } }
        await fetch(`/api/note/delete/${id}`, { method: 'DELETE', ...opt })
        await fetch(`/api/note/purge/${id}`, { method: 'DELETE', ...opt })
      }, noteId).catch(() => {})
      await page.close()
    }
  })

  test('完整工具栏功能流', async () => {
    const consoleErrors = []
    page.on('pageerror', (e) => consoleErrors.push(String(e)))

    await page.goto(`/note/edit?kbId=${KB_ID}`)
    const titleInput = page.locator('.title-input input')
    await expect(titleInput).toBeVisible()
    await titleInput.fill(NOTE_TITLE)

    const editor = page.locator('.w-e-text-container [data-slate-editor]')
    await expect(editor).toBeVisible()
    await editor.click()

    // ---- 1. 标题样式 ----
    let menu = await openMenu(page, 'headerSelect')
    await menu.locator('li[data-value="header1"]').click()
    await page.keyboard.type('一级标题')
    await page.keyboard.press('Enter')
    // 恢复正文段落
    menu = await openMenu(page, 'headerSelect')
    await menu.locator('li', { hasText: '正文' }).first().click()
    await page.keyboard.type('测试正文内容')

    // ---- 2. 加粗 / 斜体 / 下划线 ----
    for (const key of ['bold', 'italic', 'underline']) {
      await page.keyboard.press('Home')
      await page.keyboard.press('Shift+End')
      await clickMenu(page, key)
    }

    // ---- 3. 文字颜色 / 背景高亮（跳过首项“默认颜色”，选第一个彩色）----
    for (const key of ['color', 'bgColor']) {
      await page.keyboard.press('Home')
      await page.keyboard.press('Shift+End')
      menu = await openMenu(page, key)
      await menu.locator('li').nth(1).click()
    }

    // ---- 4. 字号 / 字体 / 行高 ----
    await page.keyboard.press('Home')
    await page.keyboard.press('Shift+End')
    menu = await openMenu(page, 'fontSize')
    await menu.locator('li[data-value]').nth(3).click()
    await page.keyboard.press('Home')
    await page.keyboard.press('Shift+End')
    menu = await openMenu(page, 'fontFamily')
    await menu.locator('li[data-value]').first().click()
    await page.keyboard.press('Home')
    await page.keyboard.press('Shift+End')
    menu = await openMenu(page, 'lineHeight')
    await menu.locator('li[data-value]').nth(2).click()

    // ---- 5. 无序 / 有序列表 ----
    await page.keyboard.press('End')
    await page.keyboard.press('Enter')
    await clickMenu(page, 'bulletedList')
    await page.keyboard.type('项目一')
    await page.keyboard.press('Enter')
    await page.keyboard.type('项目二')
    await page.keyboard.press('Enter')
    await clickMenu(page, 'numberedList')
    await page.keyboard.type('步骤一')
    await page.keyboard.press('Enter')
    await page.keyboard.type('步骤二')
    // Enter 产生空列表项，Backspace 删除空项并退出列表（否则后续块元素都会并入列表项）
    await page.keyboard.press('Enter')
    await page.keyboard.press('Backspace')

    // ---- 6. 居中对齐（在 group-justify 分组内，JS 点击隐藏按钮同样生效）----
    await page.keyboard.press('Control+Home')
    await page.keyboard.press('Shift+End')
    await clickMenu(page, 'justifyCenter')

    // ---- 7. 引用（blockquote）----
    await page.keyboard.press('Control+End')
    await page.keyboard.press('Enter')
    await clickMenu(page, 'blockquote')
    await page.keyboard.type('这是引用内容')

    // ---- 8. 表情 ----
    await page.keyboard.press('Control+End')
    await page.keyboard.press('Enter')
    menu = await openMenu(page, 'emotion', 'li, [class*="emoji"], .w-e-drop-panel-item')
    await menu.locator('li, [class*="emoji"], .w-e-drop-panel-item').first().click()

    // ---- 9. 链接（弹窗挂在编辑器容器下）----
    await page.keyboard.press('Enter')
    await clickMenu(page, 'insertLink')
    const linkModal = page.locator('.w-e-text-container .w-e-modal')
    await expect(linkModal).toBeVisible()
    await linkModal.locator('label:has-text("链接文本") input').fill('链接测试')
    await linkModal.locator('label:has-text("链接地址") input').fill('https://example.com')
    await linkModal.getByRole('button', { name: '确定' }).click()

    // ---- 10. 表格（3x3：选第 3 行第 3 列单元格）----
    await page.keyboard.press('Control+End')
    await page.keyboard.press('Enter')
    menu = await openMenu(page, 'insertTable', 'td')
    await menu.locator('td[data-x="2"][data-y="2"]').click()
    await page.keyboard.type('表格')

    // ---- 11. 代码块 ----
    await page.keyboard.press('Control+End')
    await page.keyboard.press('Enter')
    await clickMenu(page, 'codeBlock')
    await page.keyboard.type('print("hello")')

    // ---- 12. 分割线 ----
    await page.keyboard.press('Control+End')
    await page.keyboard.press('Enter')
    await clickMenu(page, 'divider')

    // ---- 13. 撤销 / 重做 ----
    await clickMenu(page, 'undo')
    await expect(editor.locator('hr')).toHaveCount(0)
    await clickMenu(page, 'redo')
    await expect(editor.locator('hr')).toHaveCount(1)

    // ---- 14. 全屏（key 为 fullScreen）----
    await clickMenu(page, 'fullScreen')
    await expect(page.locator('.w-e-full-screen-container')).toBeVisible()
    await clickMenu(page, 'fullScreen')
    await expect(page.locator('.w-e-full-screen-container')).toHaveCount(0)

    // ---- 15. 保存并捕获 noteId ----
    const resPromise = page.waitForResponse(
      (r) => r.url().includes('/api/note/add') && r.request().method() === 'POST'
    )
    await page.getByRole('button', { name: '保存' }).click()
    const res = await resPromise
    const body = await res.json()
    expect(body.code).toBe(200)
    noteId = body.data
    expect(noteId).toBeTruthy()

    // ---- 16. 刷新后持久化验证 ----
    await page.goto(`/note/edit?id=${noteId}`)
    await expect(page.locator('.title-input input')).toHaveValue(NOTE_TITLE)
    await expect(editor).toBeVisible()

    // 断言依据数据库保存的原始 HTML（编辑器渲染 DOM 中列表为 div+符号结构，不直接反映存储格式）
    const dbContent = await page.evaluate(async (id) => {
      const t = localStorage.getItem('token')
      const r = await fetch(`/api/note/detail/${id}`, { headers: { Authorization: `Bearer ${t}` } })
      return (await r.json()).data.content
    }, noteId)

    expect(dbContent, '标题1').toContain('<h1')
    expect(dbContent, '加粗').toContain('<strong')
    expect(dbContent, '斜体').toContain('<em')
    expect(dbContent, '下划线').toContain('<u')
    expect(dbContent, '文字颜色/高亮').toMatch(/style="[^"]*\b(color|background-color):/i)
    expect(dbContent, '字号/字体/行高').toMatch(/font-size|font-family|line-height/i)
    expect(dbContent, '无序列表').toContain('<ul')
    expect(dbContent, '有序列表').toContain('<ol')
    expect(dbContent, '居中').toContain('text-align: center')
    expect(dbContent, '引用').toContain('<blockquote')
    expect(dbContent, '链接').toContain('https://example.com')
    expect(dbContent, '表格').toContain('<table')
    expect(dbContent, '代码块').toContain('<pre')
    expect(dbContent, '分割线').toContain('<hr')

    expect(consoleErrors, '页面无 JS 报错').toEqual([])
  })
})
