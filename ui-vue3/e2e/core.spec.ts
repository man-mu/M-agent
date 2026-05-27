import { expect, test } from '@playwright/test'

test.describe('M-Agent frontend smoke', () => {
  test('loads settings and disabled skills state', async ({ page }) => {
    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: '模型设置' })).toBeVisible()
    await expect(page.getByText('当前模型')).toBeVisible()

    await page.goto('/skills')
    await expect(page.getByRole('heading', { name: 'Skill 管理' })).toBeVisible()
    await expect(page.getByText('Skill 模块未启用')).toBeVisible()
    await expect(page.getByRole('button', { name: /新建 Skill/ })).toHaveCount(0)
  })

  test('runs a quick real SSE question and opens persisted history/report', async ({ page }) => {
    const question = `Phase 5 E2E 快速验证 ${Date.now()}，请用一句话回答。`

    await page.goto('/chat')
    await expect(page.getByText('输入一个问题，启动真实模型研究流程。')).toBeVisible()

    const deepResearch = page.getByText('深度研究')
    if (await deepResearch.isVisible()) {
      await page.locator('.composer-options .ant-switch').first().click()
    }
    await expect(page.getByText('快速回答')).toBeVisible()

    await page.getByTestId('composer-input').fill(question)
    await page.getByTestId('send-message').click()

    await expect(page.getByText('工作流进度')).toBeVisible()
    await expect(page.getByText('已结束')).toBeVisible({ timeout: 120_000 })
    await expect(page.getByRole('main').getByText(question)).toBeVisible()

    const currentUrl = page.url()
    const sessionId = currentUrl.match(/\/chat\/([^/?#]+)/)?.[1]
    expect(sessionId).toBeTruthy()

    const reportHeading = page.getByRole('heading', { name: '研究报告' })
    if (!await reportHeading.isVisible()) {
      await page.getByRole('button', { name: /报告/ }).click()
    }
    await expect(reportHeading).toBeVisible()
    await expect(page.locator('.report-body')).not.toContainText('发送问题后会在这里显示研究过程和报告。')

    const detailResponse = await page.request.get(`/api/conversations/${sessionId}`)
    expect(detailResponse.ok()).toBe(true)
    const detail = await detailResponse.json()
    expect(detail.data.messages).toHaveLength(2)
    const threadId = detail.data.messages.findLast((item: { thread_id?: string }) => item.thread_id)?.thread_id
    expect(threadId).toBeTruthy()

    const reportResponse = await page.request.get(`/api/reports/${threadId}`)
    expect(reportResponse.ok()).toBe(true)
    const report = await reportResponse.json()
    expect(report.status).toBe('success')
    expect(String(report.report_information).length).toBeGreaterThan(0)

    await page.goto(`/chat/${sessionId}`)
    await expect(page.getByRole('main').getByText(question)).toBeVisible()
  })
})
