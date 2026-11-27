import { test, expect } from '@playwright/test'

const email = process.env.APM_EMAIL || 'secadmin@local'
const password = process.env.APM_PASSWORD || 'changeme'

test.describe('APM smoke', () => {
  test('login and open dashboard', async ({ page }) => {
    test.skip(!process.env.E2E_LIVE, 'Set E2E_LIVE=1 with manager+UI running')
    await page.goto('/login')
    await page.getByLabel(/email/i).fill(email)
    await page.getByLabel(/password/i).fill(password)
    await page.getByRole('button', { name: /sign in|log in/i }).click()
    await expect(page).toHaveURL(/\/$|\/dashboard/)
    await expect(page.getByText(/dashboard|performance/i).first()).toBeVisible()
  })

  test('cost page loads when enabled', async ({ page }) => {
    test.skip(!process.env.E2E_LIVE, 'Set E2E_LIVE=1 with manager+UI running')
    await page.goto('/login')
    await page.getByLabel(/email/i).fill(email)
    await page.getByLabel(/password/i).fill(password)
    await page.getByRole('button', { name: /sign in|log in/i }).click()
    const costLink = page.getByRole('link', { name: /cost/i })
    if (await costLink.count()) {
      await costLink.click()
      await expect(page.getByText(/cost/i).first()).toBeVisible()
    }
  })
})
