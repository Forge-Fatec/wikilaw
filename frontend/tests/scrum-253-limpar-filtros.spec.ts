import { expect, test, type Page } from '@playwright/test'
import { abrirFiltros, abrirFrontend, pesquisar } from './helpers/test-helper'

const DOCUMENTOS = [
  { id: 1, fonte: 'STJ', titulo: 'Acórdão do STJ', tribunal: 'STJ' },
  { id: 2, fonte: 'TJDFT', titulo: 'Acórdão do TJDFT', tribunal: 'TJDFT' },
  { id: 3, fonte: 'BDJUR', titulo: 'Artigo da BDJUR', tribunal: 'STJ' },
].map((documento) => ({
  ...documento,
  categoria: 'decisoes',
  resumoOuEmenta: 'Ementa de teste.',
  dataPublicacao: '2025-01-10',
}))

function printStep(cenario: string, etapa: string): void {
  console.info(`[limpar-filtros][${cenario}] ${etapa}`)
}

// Devolve só os documentos das fontes enviadas e guarda as URLs pedidas.
async function mockarDocumentos(page: Page) {
  const consultas: URL[] = []
  await page.route('**/api/documentos/**', async (route) => {
    const url = new URL(route.request().url())
    consultas.push(url)
    const fontes = url.searchParams.getAll('fonte')
    const itens = url.pathname.endsWith('/decisoes')
      ? DOCUMENTOS.filter((documento) => !fontes.length || fontes.includes(documento.fonte))
      : []
    await route.fulfill({
      json: { itens, pagina: 0, tamanho: 100, total: itens.length, totalPaginas: 1 },
    })
  })
  return consultas
}

function fonte(page: Page, sigla: string) {
  return page.getByRole('group', { name: 'FONTES PESQUISADAS' })
    .getByRole('checkbox', { name: sigla, exact: true })
}

function tipo(page: Page, rotulo: string) {
  return page.getByRole('group', { name: 'TIPO DE DOCUMENTO' })
    .getByRole('checkbox', { name: rotulo, exact: true })
}

const limparFiltros = (page: Page) => page.getByRole('button', { name: 'Limpar filtros' })
const contadorFiltros = (page: Page) => page.locator('.filtros-hero .filters-toggle .filters-count')

test.describe('SCRUM-253: botão para limpar os filtros da pesquisa', () => {
  test('limpa tipo, tribunal, período e fontes de uma vez', async ({ page }) => {
    const cenario = 'todos os filtros'
    await mockarDocumentos(page)
    await abrirFrontend(page)
    await pesquisar(page)
    await expect(page.getByText('3 documentos encontrados', { exact: false })).toBeVisible()

    printStep(cenario, 'Aplicando tipo, tribunal, período e fonte')
    await abrirFiltros(page)
    await tipo(page, 'Doutrina').uncheck()
    await page.locator('#painel-filtros select').selectOption('TJDFT')
    await page.locator('#painel-filtros input[type="date"]').first().fill('2025-01-01')
    await fonte(page, 'BDJUR').uncheck()
    await expect(contadorFiltros(page)).toHaveText('4')
    await expect(page.getByRole('list', { name: 'Filtros ativos' }).getByRole('listitem')).toHaveCount(3)

    printStep(cenario, 'Clicando em Limpar filtros')
    await limparFiltros(page).click()

    printStep(cenario, 'Validando que tudo voltou ao padrão')
    await expect(page.getByRole('list', { name: 'Filtros ativos' })).toHaveCount(0)
    await expect(limparFiltros(page)).toHaveCount(0)
    await expect(contadorFiltros(page)).toHaveCount(0)
    await expect(tipo(page, 'Doutrina')).toBeChecked()
    await expect(page.locator('#painel-filtros select')).toHaveValue('Todos os tribunais')
    await expect(page.locator('#painel-filtros input[type="date"]').first()).toHaveValue('')
    for (const sigla of ['STJ', 'TJDFT', 'BDJUR']) {
      await expect(fonte(page, sigla)).toBeChecked()
    }
  })

  test('aparece só com fonte desmarcada e a próxima pesquisa volta a usar todas', async ({ page }) => {
    const cenario = 'só fontes'
    const consultas = await mockarDocumentos(page)
    await abrirFrontend(page)
    await pesquisar(page)
    await expect(page.getByText('3 documentos encontrados', { exact: false })).toBeVisible()
    await expect(limparFiltros(page)).toHaveCount(0)

    printStep(cenario, 'Desmarcando uma fonte, que não vira chip')
    await abrirFiltros(page)
    await fonte(page, 'STJ').uncheck()
    await expect(page.getByRole('list', { name: 'Filtros ativos' })).toHaveCount(0)
    await expect(limparFiltros(page)).toBeVisible()

    printStep(cenario, 'Limpando e pesquisando de novo')
    await limparFiltros(page).click()
    await expect(fonte(page, 'STJ')).toBeChecked()
    await expect(limparFiltros(page)).toHaveCount(0)
    await pesquisar(page)
    await expect(page.getByText('3 documentos encontrados', { exact: false })).toBeVisible()
    expect(consultas.at(-1)?.searchParams.has('fonte')).toBe(false)
  })
})
