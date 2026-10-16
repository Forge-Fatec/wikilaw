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
  await page.route('**/api/dashboard**', (route) => route.fulfill({ json: {
    indicadores: { totalDecisoes: documents.length, classificadas: 0, favoraveis: 0, aderencia: null },
    distribuicaoMerito: [], distribuicaoCamaraTurma: [],
  } }))
  await abrirFrontend(page)
  await expect(page.getByRole('button', { name: /^pesquisar$/i })).toBeEnabled()
}

const PDF_HEADERS = { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="wikilaw-pesquisa-teste.pdf"', 'Access-Control-Expose-Headers': 'Content-Disposition' }

async function openExport(page: Page) {
  await page.getByRole('button', { name: 'Exportar PDF', exact: true }).click()
  await expect(page.getByRole('dialog', { name: 'Exportar resultados em PDF' })).toBeVisible()
}

async function confirmExport(page: Page) {
  await page.getByRole('dialog').getByRole('button', { name: 'Gerar e baixar PDF' }).click()
}

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
  await openExport(page)
  await confirmExport(page)
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
  // O painel de filtros começa fechado.
  await page.getByRole('button', { name: 'Filtros', expanded: false }).click()
  await page.getByLabel('Precedente', { exact: true }).uncheck()
  await page.getByLabel('Doutrina', { exact: true }).uncheck()
  await page.locator('select').nth(0).selectOption('TJDFT')
  await page.locator('select').nth(1).selectOption('TJDFT')
  await page.locator('.date-input').nth(0).fill('2025-01-03')
  await page.locator('.date-input').nth(1).fill('2025-01-06')
  await page.getByRole('button', { name: 'Mais antigos', exact: true }).click()
  const download = page.waitForEvent('download')
  await openExport(page)
  await confirmExport(page)
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
  await openExport(page)
  await confirmExport(page)
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
  await openExport(page)
  await confirmExport(page)
  await expect(page.getByRole('alert')).toHaveText('Não foi possível gerar o PDF. Tente novamente.')
  const download = page.waitForEvent('download')
  await confirmExport(page)
  await download
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('does not download a successful response that is not a PDF', async ({ page }) => {
  let downloads = 0
  page.on('download', () => downloads++)
  await page.route('**/api/pesquisas/exportacoes/pdf', (route) => route.fulfill({ json: { error: 'unexpected JSON' } }))
  await prepare(page)
  await openExport(page)
  await confirmExport(page)
  await expect(page.getByRole('alert')).toHaveText('O arquivo recebido não é um PDF válido. Tente novamente.')
  expect(downloads).toBe(0)
})

test('asks to refresh when a result has been removed', async ({ page }) => {
  await page.route('**/api/pesquisas/exportacoes/pdf', (route) => route.fulfill({ status: 409, json: {} }))
  await prepare(page)
  await openExport(page)
  await confirmExport(page)
  await expect(page.getByRole('alert')).toHaveText('Um resultado não está mais disponível. Atualize a pesquisa e tente novamente.')
})

test('opens the dialog without starting an export and restores focus when cancelled', async ({ page }) => {
  let requests = 0
  await page.route('**/api/pesquisas/exportacoes/pdf', (route) => { requests++; return route.fulfill({ headers: PDF_HEADERS, body: PDF }) })
  await prepare(page)
  await openExport(page)
  await expect(page.getByLabel('Todas as páginas', { exact: false })).toBeFocused()
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Exportar PDF', exact: true })).toBeFocused()
  await openExport(page)
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(requests).toBe(0)
})

test('exports only the chosen result pages, including a partial last page', async ({ page }, testInfo) => {
  let payload: Record<string, unknown> | undefined
  await page.route('**/api/pesquisas/exportacoes/pdf', (route) => {
    payload = route.request().postDataJSON()
    return route.fulfill({ headers: PDF_HEADERS, body: PDF })
  })
  await prepare(page)
  await openExport(page)
  await page.getByLabel('Selecionar intervalo', { exact: true }).check()
  await page.getByLabel('Da página', { exact: true }).fill('2')
  await page.getByLabel('Até a página', { exact: true }).fill('2')
  await expect(page.getByText('2 documentos serão incluídos no PDF.', { exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('export-dialog.png') })
  const download = page.waitForEvent('download')
  await confirmExport(page)
  await download
  expect(payload).toMatchObject({
    intervaloPaginas: { de: 2, ate: 2, totalResultados: 12, itensPorPagina: 10 },
    resultados: [{ categoria: 'decisoes', id: 2 }, { categoria: 'decisoes', id: 1 }],
  })
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('rejects inverted, empty, fractional and out-of-bounds intervals before any request', async ({ page }) => {
  let requests = 0
  await page.route('**/api/pesquisas/exportacoes/pdf', (route) => { requests++; return route.fulfill({ headers: PDF_HEADERS, body: PDF }) })
  await prepare(page)
  await openExport(page)
  await page.getByLabel('Selecionar intervalo', { exact: true }).check()
  for (const [from, to] of [['2', '1'], ['0', '1'], ['', '2'], ['1.5', '2'], ['1', '3']]) {
    await page.getByLabel('Da página', { exact: true }).fill(from)
    await page.getByLabel('Até a página', { exact: true }).fill(to)
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Gerar e baixar PDF' })).toBeDisabled()
  }
  expect(requests).toBe(0)
})

test('allows selecting a smaller interval when the full search exceeds the PDF limit', async ({ page }) => {
  let selected: { resultados: { id: number }[] } | undefined
  await page.route('**/api/pesquisas/exportacoes/pdf', (route) => {
    selected = route.request().postDataJSON()
    return route.fulfill({ headers: PDF_HEADERS, body: PDF })
  })
  const documents = Array.from({ length: 5001 }, (_, index) => ({ ...DOCUMENTS[0], id: index + 1 }))
  await prepare(page, documents)
  await openExport(page)
  await expect(page.getByRole('button', { name: 'Gerar e baixar PDF' })).toBeDisabled()
  await expect(page.getByRole('alert')).toHaveText('Selecione um intervalo com até 5.000 documentos por PDF.')
  await page.getByLabel('Selecionar intervalo', { exact: true }).check()
  await page.getByLabel('Da página', { exact: true }).fill('501')
  await page.getByLabel('Até a página', { exact: true }).fill('501')
  await expect(page.getByText('1 documento será incluído no PDF.', { exact: true })).toBeVisible()
  const download = page.waitForEvent('download')
  await confirmExport(page)
  await download
  expect(selected?.resultados).toEqual([{ categoria: 'decisoes', id: 5001 }])
})

test('keeps keyboard focus inside the dialog and fits a narrow screen', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await prepare(page)
  await openExport(page)
  await page.getByRole('button', { name: 'Gerar e baixar PDF' }).focus()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: 'Fechar exportação' })).toBeFocused()
  await page.keyboard.press('Shift+Tab')
  await expect(page.getByRole('button', { name: 'Gerar e baixar PDF' })).toBeFocused()
  const box = await page.getByRole('dialog').boundingBox()
  expect(box).not.toBeNull()
  expect(box!.x).toBeGreaterThanOrEqual(0)
  expect(box!.x + box!.width).toBeLessThanOrEqual(390)
  await page.screenshot({ path: testInfo.outputPath('export-dialog-mobile.png') })
})
