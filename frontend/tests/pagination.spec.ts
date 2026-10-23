import { expect, test, type Page } from '@playwright/test'
import { paginasVisiveis } from '../src/paginacao'
import { abrirEPesquisar } from './helpers/test-helper'

const TOTAL_DOCUMENTOS = 500 // 50 páginas de 10 na tela

function printStep(cenario: string, etapa: string): void {
  console.info(`[paginacao][${cenario}] ${etapa}`)
}

function documentos(inicio: number, quantidade: number) {
  return Array.from({ length: quantidade }, (_, indice) => ({
    id: inicio + indice,
    fonte: 'TESTE',
    categoria: 'decisoes',
    titulo: `Documento ${inicio + indice}`,
    tipoDocumento: null,
    numeroProcessoOuTema: null,
    autoresOuRelator: null,
    resumoOuEmenta: null,
    decisao: null,
    tribunal: null,
    orgaoJulgador: null,
    dataJulgamento: null,
    dataPublicacao: '2026-01-15',
    dataOriginal: null,
    urlOriginal: null,
    idRegistroBruto: null,
  }))
}

async function mockarApi(page: Page) {
  await page.route('**/api/documentos/**', async (route) => {
    const url = new URL(route.request().url())
    const pagina = Number(url.searchParams.get('pagina') ?? 0)
    const total = url.pathname.endsWith('/decisoes') ? TOTAL_DOCUMENTOS : 0
    const inicio = pagina * 100
    await route.fulfill({
      json: {
        itens: documentos(inicio + 1, Math.max(0, Math.min(100, total - inicio))),
        pagina,
        tamanho: 100,
        total,
        totalPaginas: Math.ceil(total / 100),
      },
    })
  })
  await page.route('**/api/dashboard*', (route) => route.fulfill({ status: 503 }))
}

function paginacao(page: Page) {
  return page.getByRole('navigation', { name: 'Páginas de resultados' })
}

async function paginasExibidas(page: Page): Promise<string[]> {
  return paginacao(page)
    .locator('button, .pagination-ellipsis')
    .evaluateAll((itens) => itens.map((item) => item.textContent?.trim() ?? ''))
}

test.describe('paginação dos resultados', () => {
  test('mostra todas as páginas quando são poucas', () => {
    expect(paginasVisiveis(1, 1)).toEqual([1])
    expect(paginasVisiveis(3, 7)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  test('resume as páginas com início, vizinhas da atual e fim', () => {
    expect(paginasVisiveis(1, 50)).toEqual([1, 2, 3, '…', 49, 50])
    expect(paginasVisiveis(25, 50)).toEqual([1, 2, '…', 23, 24, 25, 26, 27, '…', 49, 50])
    expect(paginasVisiveis(50, 50)).toEqual([1, 2, '…', 48, 49, 50])
  })

  test('mostra o número em vez de reticências para um salto de uma página', () => {
    expect(paginasVisiveis(5, 50)).toEqual([1, 2, 3, 4, 5, 6, 7, '…', 49, 50])
    expect(paginasVisiveis(6, 50)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, '…', 49, 50])
    expect(paginasVisiveis(7, 50)).toEqual([1, 2, '…', 5, 6, 7, 8, 9, '…', 49, 50])
  })

  test('navega entre páginas sem listar um botão para cada uma', async ({ page }) => {
    const cenario = '50 páginas'

    printStep(cenario, 'Configurando mock da API')
    await mockarApi(page)

    printStep(cenario, 'Abrindo o frontend e pesquisando')
    await abrirEPesquisar(page)
    await expect(page.getByText('500 documentos encontrados · Página 1 de 50')).toBeVisible()
    expect(await paginasExibidas(page)).toEqual([
      'ANTERIOR', '1', '2', '3', '…', '49', '50', 'PRÓXIMA',
    ])

    printStep(cenario, 'Indo para a última página')
    await paginacao(page).getByRole('button', { name: '50', exact: true }).click()
    await expect(page.getByText('Página 50 de 50')).toBeVisible()
    await expect(paginacao(page).getByRole('button', { name: 'PRÓXIMA' })).toBeDisabled()

    printStep(cenario, 'Voltando uma página e saltando para o meio')
    await paginacao(page).getByRole('button', { name: 'ANTERIOR' }).click()
    await paginacao(page).getByRole('button', { name: '47', exact: true }).click()
    await expect(page.getByText('Página 47 de 50')).toBeVisible()
    expect(await paginasExibidas(page)).toEqual([
      'ANTERIOR', '1', '2', '…', '45', '46', '47', '48', '49', '50', 'PRÓXIMA',
    ])
    await expect(paginacao(page).getByRole('button', { name: '47', exact: true }))
      .toHaveAttribute('aria-current', 'page')
  })
})
