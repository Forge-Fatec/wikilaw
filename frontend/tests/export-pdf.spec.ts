import { test, expect, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { abrirFrontend } from './helpers/test-helper'

function pdfFixture(): Buffer {
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>',
  ]
  let pdf = '%PDF-1.4\n'
  const offsets = [0]
  objects.forEach((value, index) => {
    offsets.push(Buffer.byteLength(pdf))
    pdf += `${index + 1} 0 obj\n${value}\nendobj\n`
  })
  const xref = Buffer.byteLength(pdf)
  pdf += `xref\n0 4\n0000000000 65535 f \n${offsets.slice(1).map((offset) => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}`
  pdf += `trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  return Buffer.from(pdf)
}

const PDF = pdfFixture()
const DOCUMENTS = Array.from({ length: 12 }, (_, index) => ({
  id: index + 1, fonte: index < 6 ? 'TJDFT' : 'STJ', categoria: 'decisoes',
  titulo: `Acórdão ${index + 1}`, resumoOuEmenta: 'Proteção do consumidor.', decisao: 'Recurso provido.',
  tribunal: index < 6 ? 'TJDFT' : 'STJ', dataPublicacao: `2025-01-${String(index + 1).padStart(2, '0')}`,
}))

async function prepare(page: Page, documents = DOCUMENTS) {
  await page.route('**/api/documentos/**', (route) => route.fulfill({
    json: { itens: route.request().url().includes('/decisoes') ? documents : [], pagina: 0, tamanho: 100, total: documents.length, totalPaginas: 1 },
  }))
  await page.route('**/api/jurisprudencia/**', (route) => route.fulfill({ json: [] }))
  await abrirFrontend(page)
  await expect(page.getByRole('button', { name: /^pesquisar$/i })).toBeEnabled()
}

const PDF_HEADERS = { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="wikilaw-pesquisa-teste.pdf"', 'Access-Control-Expose-Headers': 'Content-Disposition' }

test('downloads a PDF for every filtered result, including other UI pages', async ({ page }, testInfo) => {
  let payload: { resultados: { id: number }[], termo: string } | undefined
  await page.route('**/api/pesquisas/exportacoes/pdf', (route) => {
    payload = route.request().postDataJSON()
    expect(route.request().method()).toBe('POST')
    return route.fulfill({ headers: PDF_HEADERS, body: PDF })
  })
  await prepare(page)
  await page.getByRole('textbox', { name: 'Descrição do caso' }).fill('negativação indevida')
  await page.getByRole('button', { name: /^pesquisar$/i }).click()
  await expect(page.getByText('12 documentos encontrados', { exact: false })).toBeVisible()
  // Editing the input without submitting a new search must not relabel old results.
  await page.getByRole('textbox', { name: 'Descrição do caso' }).fill('outro caso ainda não pesquisado')
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Exportar PDF' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('wikilaw-pesquisa-teste.pdf')
  const path = testInfo.outputPath('pesquisa.pdf')
  await download.saveAs(path)
  expect((await readFile(path)).subarray(0, 5).toString()).toBe('%PDF-')
  expect(payload?.resultados.map((row) => row.id)).toEqual([12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1])
  expect(payload?.termo).toBe('negativação indevida')
  await expect(page.getByRole('status')).toHaveText('PDF gerado. O download foi iniciado.')
  await page.locator('.results-header').screenshot({ path: testInfo.outputPath('export-button.png') })
})

test('exports the applied category, source, tribunal, period and order', async ({ page }) => {
  let payload: Record<string, unknown> | undefined
  await page.route('**/api/pesquisas/exportacoes/pdf', (route) => {
    payload = route.request().postDataJSON()
    return route.fulfill({ headers: PDF_HEADERS, body: PDF })
  })
  await prepare(page)
  await page.getByLabel('Precedente', { exact: true }).uncheck()
  await page.getByLabel('Doutrina', { exact: true }).uncheck()
  await page.locator('select').nth(0).selectOption('TJDFT')
  await page.locator('select').nth(1).selectOption('TJDFT')
  await page.locator('.date-input').nth(0).fill('2025-01-03')
  await page.locator('.date-input').nth(1).fill('2025-01-06')
  await page.getByRole('button', { name: 'Mais antigos', exact: true }).click()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Exportar PDF' }).click()
  await download
  expect(payload).toMatchObject({ termo: '', categorias: ['decisoes'], fonte: 'TJDFT', tribunal: 'TJDFT', dataDe: '2025-01-03', dataAte: '2025-01-06', ordenacao: 'mais-antigos', resultados: [
    { categoria: 'decisoes', id: 3 }, { categoria: 'decisoes', id: 4 }, { categoria: 'decisoes', id: 5 }, { categoria: 'decisoes', id: 6 },
  ] })
})

test('disables export when there are no results', async ({ page }) => {
  await prepare(page, [])
  await expect(page.getByRole('button', { name: 'Exportar PDF' })).toBeDisabled()
})

test('shows generation progress and prevents duplicate requests', async ({ page }) => {
  let release!: () => void
  const pending = new Promise<void>((resolve) => { release = resolve })
  let requests = 0
  await page.route('**/api/pesquisas/exportacoes/pdf', async (route) => {
    requests++
    await pending
    await route.fulfill({ headers: PDF_HEADERS, body: PDF })
  })
  await prepare(page)
  await page.getByRole('button', { name: 'Exportar PDF' }).click()
  await expect(page.getByRole('button', { name: 'Gerando PDF...' })).toBeDisabled()
  await expect.poll(() => requests).toBe(1)
  const download = page.waitForEvent('download')
  release()
  await download
  await expect(page.getByRole('button', { name: 'Exportar PDF' })).toBeEnabled()
})

test('handles generation failure and allows retry', async ({ page }) => {
  let attempt = 0
  await page.route('**/api/pesquisas/exportacoes/pdf', (route) => ++attempt === 1
    ? route.fulfill({ status: 500, json: { error: 'Error' } })
    : route.fulfill({ headers: PDF_HEADERS, body: PDF }))
  await prepare(page)
  await page.getByRole('button', { name: 'Exportar PDF' }).click()
  await expect(page.getByRole('alert')).toHaveText('Não foi possível gerar o PDF. Tente novamente.')
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Exportar PDF' }).click()
  await download
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('does not download a successful response that is not a PDF', async ({ page }) => {
  let downloads = 0
  page.on('download', () => downloads++)
  await page.route('**/api/pesquisas/exportacoes/pdf', (route) => route.fulfill({ json: { error: 'unexpected JSON' } }))
  await prepare(page)
  await page.getByRole('button', { name: 'Exportar PDF' }).click()
  await expect(page.getByRole('alert')).toHaveText('O arquivo recebido não é um PDF válido. Tente novamente.')
  expect(downloads).toBe(0)
})

test('asks to refresh when a result has been removed', async ({ page }) => {
  await page.route('**/api/pesquisas/exportacoes/pdf', (route) => route.fulfill({ status: 409, json: {} }))
  await prepare(page)
  await page.getByRole('button', { name: 'Exportar PDF' }).click()
  await expect(page.getByRole('alert')).toHaveText('Um resultado não está mais disponível. Atualize a pesquisa e tente novamente.')
})
