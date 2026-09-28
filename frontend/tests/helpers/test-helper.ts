import type { Page } from '@playwright/test'

export const FRONTEND_URL =
  process.env.FRONTEND_URL ?? 'http://localhost:3000'

export async function abrirFrontend(page: Page): Promise<void> {
  await page.goto(FRONTEND_URL, { waitUntil: 'domcontentloaded' })
}
