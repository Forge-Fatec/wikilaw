import { expect, test } from '@playwright/test'
import { resolveTheme } from '../src/theme'
import { abrirFrontend, entrarComoAdmin, FRONTEND_URL, pesquisar, simularSessao } from './helpers/test-helper'

test.use({ colorScheme: 'light' })

test.describe('resolveTheme — unidade', () => {
  test('preserva a escolha clara mesmo com sistema escuro', () => {
    expect(resolveTheme('light', true)).toBe('light')
  })
  test('preserva a escolha escura mesmo com sistema claro', () => {
    expect(resolveTheme('dark', false)).toBe('dark')
  })
  test('usa o sistema quando a preferência está ausente ou inválida', () => {
    for (const value of [null, undefined, '', 'invalid', 42]) {
      expect(resolveTheme(value, true)).toBe('dark')
      expect(resolveTheme(value, false)).toBe('light')
    }
  })
})

test.describe('SCRUM-254 — modo claro e escuro', () => {
  test('acompanha a preferência do sistema enquanto não há escolha salva', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' })
    await simularSessao(page)
    await page.goto(FRONTEND_URL)
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    await expect(page.getByRole('button', { name: 'Ativar modo claro' })).toHaveAttribute('aria-pressed', 'true')
    await expect(page.locator('html')).toHaveCSS('color-scheme', 'dark')
    await page.emulateMedia({ colorScheme: 'light' })
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
  })

  test('alterna pelo teclado e mantém a escolha no login, na aplicação e após recarregar', async ({ page }) => {
    await simularSessao(page)
    await page.route('**/api/fontes', route => route.fulfill({ json: [] }))
    await page.goto(FRONTEND_URL)
    const toggle = page.getByRole('button', { name: 'Ativar modo escuro' })
    await toggle.focus()
    await page.keyboard.press('Enter')
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    expect(await page.evaluate(() => localStorage.getItem('wikilaw:theme'))).toBe('dark')
    await entrarComoAdmin(page)
    await expect(page.locator('.header').getByRole('button', { name: 'Ativar modo claro' })).toBeVisible()
    const input = page.locator('.hero').getByLabel('Descrição do caso')
    await input.fill('dano moral')
    await page.getByRole('button', { name: 'Ativar modo claro' }).click()
    await expect(input).toHaveValue('dano moral')
    await page.getByRole('button', { name: 'Ativar modo escuro' }).click()
    await page.reload()
    await expect(page.getByRole('heading', { name: 'Bem-vindo de volta.' })).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    await expect(page.getByRole('button', { name: 'Ativar modo claro' })).toBeVisible()
    await page.emulateMedia({ colorScheme: 'dark' })
    await page.emulateMedia({ colorScheme: 'light' })
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  })

  test('a escolha salva tem prioridade sobre o sistema desde a abertura', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' })
    await page.addInitScript(() => localStorage.setItem('wikilaw:theme', 'light'))
    await simularSessao(page)
    await page.goto(FRONTEND_URL)
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
    await expect(page.locator('.login-panel')).toHaveCSS('background-color', 'rgb(248, 246, 242)')
  })

  test('ignora uma preferência salva inválida', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' })
    await page.addInitScript(() => localStorage.setItem('wikilaw:theme', 'invalid'))
    await simularSessao(page)
    await page.goto(FRONTEND_URL)
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  })

  test('continua alternando quando o armazenamento está bloqueado', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', error => errors.push(error.message))
    await page.addInitScript(() => {
      Object.defineProperty(window, 'localStorage', { get() { throw new DOMException('Blocked', 'SecurityError') } })
    })
    await simularSessao(page)
    await page.goto(FRONTEND_URL)
    await page.getByRole('button', { name: 'Ativar modo escuro' }).click()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    await page.emulateMedia({ colorScheme: 'dark' })
    await page.emulateMedia({ colorScheme: 'light' })
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    await page.getByRole('button', { name: 'Ativar modo claro' }).click()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
    expect(errors).toEqual([])
  })

  test('sincroniza a escolha entre abas e volta ao sistema quando ela é removida', async ({ page, context }) => {
    await page.emulateMedia({ colorScheme: 'dark' })
    await simularSessao(page)
    await page.goto(FRONTEND_URL)
    const other = await context.newPage()
    await simularSessao(other)
    await other.goto(FRONTEND_URL)
    await other.evaluate(() => localStorage.setItem('wikilaw:theme', 'light'))
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
    await other.evaluate(() => localStorage.removeItem('wikilaw:theme'))
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    await other.close()
  })

  test('mantém o controle acessível no celular sem rolagem horizontal', async ({ page }, testInfo) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.emulateMedia({ colorScheme: 'dark' })
    await simularSessao(page)
    await page.goto(FRONTEND_URL)
    await expect(page.getByRole('button', { name: 'Ativar modo claro' })).toBeInViewport()
    await page.screenshot({ path: testInfo.outputPath('login-dark-mobile.png'), fullPage: true })
    await page.route('**/api/fontes', route => route.fulfill({ json: [] }))
    await entrarComoAdmin(page)
    const toggle = page.getByRole('button', { name: 'Ativar modo claro' })
    await expect(toggle).toBeInViewport()
    await toggle.click()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath('search-light-mobile.png'), fullPage: true })
  })

  test('aplica as cores aos resultados, dashboard e diálogo de exportação', async ({ page }, testInfo) => {
    await page.emulateMedia({ colorScheme: 'dark' })
    await page.route('**/api/documentos/**', route => {
      const itens = new URL(route.request().url()).pathname.endsWith('/decisoes') ? [{
        id: 1, fonte: 'STJ', categoria: 'decisoes', titulo: 'Acórdão de teste',
        resumoOuEmenta: 'Negativação indevida gera dano moral.', tribunal: 'STJ', dataPublicacao: '2025-01-01',
      }] : []
      return route.fulfill({ json: { itens, pagina: 0, tamanho: 100, total: itens.length, totalPaginas: 1 } })
    })
    await abrirFrontend(page)
    await pesquisar(page)
    await expect(page.locator('.card').first()).toContainText('Acórdão de teste')
    await expect(page.locator('.card').first()).toHaveCSS('background-color', 'rgb(36, 33, 46)')
    await expect(page.locator('.card').first()).toHaveCSS('color', 'rgb(236, 232, 242)')
    await expect(page.locator('.indicador').first()).toHaveCSS('background-color', 'rgb(36, 33, 46)')
    await page.screenshot({ path: testInfo.outputPath('results-dark-desktop.png'), fullPage: true })
    await page.getByRole('button', { name: 'Exportar PDF' }).click()
    await expect(page.getByRole('dialog')).toHaveCSS('background-color', 'rgb(36, 33, 46)')
    await page.keyboard.press('Escape')
    await page.getByRole('button', { name: 'Ativar modo claro' }).click()
    await expect(page.locator('.card').first()).toHaveCSS('background-color', 'rgb(255, 255, 255)')
    await page.screenshot({ path: testInfo.outputPath('results-light-desktop.png'), fullPage: true })
  })
})
