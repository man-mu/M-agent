import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

type ApiEnvelope<T> = {
  code?: number
  status?: string
  data?: T
  report_information?: T
  session_history?: T
}

type ConversationDetail = {
  messages: Array<{ role: string, thread_id?: string }>
}

type SessionHistory = {
  thread_id: string
  status: string
  report_thread_id?: string
}

async function getEnvelope<T>(request: APIRequestContext, url: string) {
  const response = await request.get(url)
  expect(response.ok(), `${url} should return ok`).toBe(true)
  return await response.json() as ApiEnvelope<T>
}

function dataOf<T>(payload: ApiEnvelope<T>) {
  return (payload.data ?? payload.report_information ?? payload.session_history) as T
}

function latestThreadId(detail: ConversationDetail) {
  return [...detail.messages].reverse().find(item => item.thread_id)?.thread_id || ''
}

async function assertNoHorizontalOverflow(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)
  expect(overflow).toBe(false)
}

async function ensureReportOpen(page: Page) {
  const panel = page.getByTestId('report-panel')
  await expect(panel).toBeAttached()
  const isOpen = await panel.evaluate(element => element.classList.contains('open'))
  if (!isOpen) {
    await page.getByTestId('toggle-report').click()
  }
  await expect(panel).toHaveClass(/open/)
}

async function switchQuickAnswer(page: Page) {
  await page.goto('/chat')
  await expect(page.getByTestId('chat-empty-state')).toBeVisible()
  await expect(page.getByTestId('research-status-bar')).toContainText('模型')

  const mode = page.getByTestId('mode-status')
  if (await mode.getByText('深度研究').isVisible()) {
    await page.getByTestId('deep-research-switch').click()
  }

  await expect(mode).toContainText('快速回答')
  await expect(page.getByTestId('plan-gate-status')).toContainText('无需计划')
}

async function runQuickQuestion(page: Page, question: string) {
  await switchQuickAnswer(page)
  await page.getByTestId('composer-input').fill(question)
  await page.getByTestId('send-message').click()

  await expect(page.getByTestId('workflow-progress')).toBeVisible()
  await expect(page.getByTestId('session-status')).toContainText('已完成', { timeout: 120_000 })
  await expect(page.getByRole('main').getByText(question)).toBeVisible()

  const sessionId = page.url().match(/\/chat\/([^/?#]+)/)?.[1] || ''
  expect(sessionId).toBeTruthy()
  return sessionId
}

test.describe('M-Agent demo acceptance', () => {
  test('loads workbench, settings, and disabled skills state', async ({ page, request }) => {
    const currentModel = await request.get('/api/model/current')
    expect(currentModel.ok()).toBe(true)

    await page.goto('/chat')
    await expect(page.getByTestId('chat-empty-state')).toBeVisible()
    await expect(page.getByTestId('mode-status')).toContainText('深度研究')
    await expect(page.getByTestId('plan-gate-status')).toContainText('先审阅计划')
    await expect(page.getByTestId('current-model-status')).not.toContainText('读取中')

    await page.getByTestId('current-model-status').click()
    await expect(page).toHaveURL(/\/settings$/)
    await expect(page.getByTestId('current-model-card')).toBeVisible()
    await expect(page.getByTestId('provider-card').first()).toBeVisible()

    await page.goto('/skills')
    await expect(page.getByTestId('skills-page')).toBeVisible()
    await expect(page.getByTestId('skills-disabled')).toBeVisible()
    await expect(page.getByRole('button', { name: /新建 Skill/ })).toHaveCount(0)
  })

  test('runs quick real SSE and restores persisted history/report', async ({ page, request }) => {
    const question = `Phase 7.6 快速验收 ${Date.now()}，请用一句话回答。`
    const sessionId = await runQuickQuestion(page, question)

    await ensureReportOpen(page)
    await expect(page.getByTestId('report-status')).toContainText('已生成', { timeout: 60_000 })
    await expect(page.getByTestId('report-body')).toContainText(/.+/)

    const detail = dataOf<ConversationDetail>(await getEnvelope(request, `/api/conversations/${sessionId}`))
    expect(detail.messages.length).toBeGreaterThanOrEqual(2)
    const threadId = latestThreadId(detail)
    expect(threadId).toBeTruthy()

    const reportExists = dataOf<boolean>(await getEnvelope(request, `/api/reports/${threadId}/exists`))
    expect(reportExists).toBe(true)
    const history = dataOf<SessionHistory[]>(await getEnvelope(request, `/api/sessions/${sessionId}/history`))
    expect(history.some(item => item.thread_id === threadId && item.status === 'COMPLETED')).toBe(true)

    await page.goto(`/chat/${sessionId}`)
    await expect(page.getByRole('main').getByText(question)).toBeVisible()
    await expect(page.getByTestId('conversation-item').filter({ hasText: question }).first()).toContainText('已完成')
  })

  test('shows plan gate and resumes deep research through the real backend', async ({ page, request }) => {
    const question = `Phase 7.6 Plan Gate ${Date.now()}：用两步研究 Java 17 record 的适用场景。`

    await page.goto('/chat')
    await expect(page.getByTestId('mode-status')).toContainText('深度研究')
    await expect(page.getByTestId('plan-gate-status')).toContainText('先审阅计划')
    await page.getByTestId('composer-input').fill(question)
    await page.getByTestId('send-message').click()

    await expect(page.getByTestId('plan-review')).toBeVisible({ timeout: 120_000 })
    await expect(page.getByTestId('session-status')).toContainText('等待计划确认')

    const sessionId = page.url().match(/\/chat\/([^/?#]+)/)?.[1] || ''
    expect(sessionId).toBeTruthy()
    const waitingHistory = dataOf<SessionHistory[]>(await getEnvelope(request, `/api/sessions/${sessionId}/history`))
    const pausedThread = waitingHistory.find(item => item.status === 'PAUSED')?.thread_id
    expect(pausedThread).toBeTruthy()

    await page.getByTestId('accept-plan').click()
    await expect(page.getByTestId('session-status')).toContainText('已完成', { timeout: 180_000 })

    const completedHistory = dataOf<SessionHistory[]>(await getEnvelope(request, `/api/sessions/${sessionId}/history`))
    const completed = completedHistory.find(item => item.thread_id === pausedThread)
    expect(completed?.status).toBe('COMPLETED')
    expect(completed?.report_thread_id).toBeTruthy()
  })

  test('keeps mobile demo surfaces within the viewport', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/chat')
    await expect(page.getByTestId('research-status-bar')).toBeVisible()
    await assertNoHorizontalOverflow(page)

    const sessionId = await runQuickQuestion(page, `Phase 7.6 移动端验收 ${Date.now()}，请用一句话回答。`)
    await ensureReportOpen(page)
    await assertNoHorizontalOverflow(page)

    await page.getByTestId('close-report').click()
    await expect(page.getByTestId('report-panel')).not.toHaveClass(/open/)

    await page.goto(`/chat/${sessionId}`)
    await expect(page.getByTestId('conversation-list')).toBeVisible()
    const listScrollsHorizontally = await page.getByTestId('conversation-list').evaluate(element =>
      element.scrollWidth > element.clientWidth,
    )
    expect(listScrollsHorizontally).toBe(true)

    await page.goto('/settings')
    await expect(page.getByTestId('settings-page')).toBeVisible()
    await assertNoHorizontalOverflow(page)

    await page.goto('/skills')
    await expect(page.getByTestId('skills-disabled')).toBeVisible()
    await assertNoHorizontalOverflow(page)
  })
})
