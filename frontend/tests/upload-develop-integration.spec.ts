import { expect, test } from '@playwright/test'
import { abrirFrontend, abrirFiltros } from './helpers/test-helper'

for (const similarity of [false, true]) {
  test(`authenticated upload preserves source filters in ${similarity ? 'similarity' : 'textual'} search`, async ({ page }) => {
    const text = 'Indenização por negativação indevida e dano moral ao consumidor.'
    const documents: string[] = []
    const postTokens: string[] = []
    await page.route('**/api/pesquisas/similaridade/status', route => route.fulfill({
      json: { habilitada: similarity, indexados: 2, pendentes: 0 },
    }))
    await page.route('**/api/pesquisas/documentos', route => {
      postTokens.push(route.request().headers()['x-xsrf-token'])
      return route.fulfill({ json: {
        nome: 'caso.pdf', formato: 'PDF', tamanhoBytes: 20,
        caracteres: text.length, paginas: 1, texto: text,
      } })
    })
    await page.route('**/api/pesquisas/contexto', route => {
      postTokens.push(route.request().headers()['x-xsrf-token'])
      expect(route.request().postDataJSON().texto).toBe(text)
      return route.fulfill({ json: { consulta: 'negativação consumidor' } })
    })
    await page.route('**/api/documentos/**', route => {
      documents.push(route.request().url())
      return route.fulfill({ json: { itens: [], pagina: 0, tamanho: 50, total: 0, totalPaginas: 0 } })
    })
    await page.route('**/api/pesquisas/similaridade', route => {
      postTokens.push(route.request().headers()['x-xsrf-token'])
      expect(route.request().postDataJSON().texto).toBe(text)
      return route.fulfill({ json: {
        itens: ['STJ', 'TJDFT'].map((fonte, index) => ({
          documento: { id: index + 1, categoria: 'decisoes', fonte, titulo: `Result ${fonte}`,
            resumoOuEmenta: text, dataPublicacao: '2026-01-01' },
          similaridade: 0.8, trecho: text,
        })), indexados: 2, pendentes: 0, limite: 200,
      } })
    })
    await page.route('**/api/dashboard/resultados', route => {
      postTokens.push(route.request().headers()['x-xsrf-token'])
      return route.fulfill({ json: {
        indicadores: { totalDecisoes: 1, classificadas: 0, favoraveis: 0, aderencia: null },
        distribuicaoMerito: [], distribuicaoCamaraTurma: [], distribuicaoTribunal: [], evolucaoAnual: [],
      } })
    })
    await abrirFrontend(page)
    expect(documents).toHaveLength(0)
    if (similarity) await expect(page.locator('.hero').getByRole('radio', { name: 'Similaridade', exact: true })).toBeChecked()
    await abrirFiltros(page)
    await page.getByRole('checkbox', { name: 'TJDFT', exact: true }).uncheck()
    await page.getByRole('checkbox', { name: 'BDJUR', exact: true }).uncheck()
    await page.locator('.hero input[type=file]').setInputFiles({ name: 'caso.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF mock upload') })
    await expect(page.locator('.hero .case-document-card')).toContainText('caso.pdf')
    await page.locator('.hero').getByRole('button', { name: 'PESQUISAR', exact: true }).click()
    if (similarity) {
      await expect(page.locator('.card h3')).toHaveText(['Result STJ'])
    } else {
      await expect.poll(() => documents.length).toBe(3)
      for (const url of documents) {
        expect(new URL(url).searchParams.getAll('fonte')).toEqual(['STJ'])
        expect(new URL(url).searchParams.get('termo')).toBe('negativação consumidor')
      }
    }
    expect(postTokens.length).toBeGreaterThanOrEqual(2)
    expect(postTokens.every(token => token === 'csrf-teste')).toBe(true)
  })
}
