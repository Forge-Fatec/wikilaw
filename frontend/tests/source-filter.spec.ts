import { expect, test, type Page } from '@playwright/test'
import { abrirFrontend, FRONTEND_URL, pesquisar } from './helpers/test-helper'

const DOCUMENTOS = [
  { id: 1, fonte: 'STJ', titulo: 'Acórdão do STJ' },
  { id: 2, fonte: 'TJDFT', titulo: 'Acórdão do TJDFT' },
  { id: 3, fonte: 'BDJUR', titulo: 'Artigo da BDJUR' },
].map((documento) => ({
  ...documento,
  categoria: 'decisoes',
  resumoOuEmenta: 'Ementa de teste.',
  dataPublicacao: '2025-01-10',
}))

function printStep(cenario: string, etapa: string): void {
  console.info(`[fontes][${cenario}] ${etapa}`)
}

// Registra as URLs pedidas e devolve só os documentos das fontes enviadas.
async function mockarApi(page: Page) {
  const documentos: URL[] = []
  const dashboard: URL[] = []
  await page.route('**/api/documentos/**', async (route) => {
    const url = new URL(route.request().url())
    documentos.push(url)
    const fontes = url.searchParams.getAll('fonte')
    const itens = url.pathname.endsWith('/decisoes')
      ? DOCUMENTOS.filter((documento) => !fontes.length || fontes.includes(documento.fonte))
      : []
    await route.fulfill({
      json: { itens, pagina: 0, tamanho: 100, total: itens.length, totalPaginas: 1 },
    })
  })
  await page.route('**/api/dashboard?*', async (route) => {
    dashboard.push(new URL(route.request().url()))
    await route.fulfill({ status: 500 })
  })
  return { documentos, dashboard }
}

function fonte(page: Page, sigla: string) {
  return page.getByRole('group', { name: 'FONTES PESQUISADAS' })
    .getByRole('checkbox', { name: sigla, exact: true })
}

test.describe('filtro de fontes da tela inicial', () => {
  test('ao entrar mostra só a busca e as fontes, todas marcadas, sem listar nada', async ({ page }) => {
    const cenario = 'tela inicial'

    printStep(cenario, 'Abrindo o frontend')
    const consultas = await mockarApi(page)
    await abrirFrontend(page)

    printStep(cenario, 'Validando a busca e as fontes marcadas')
    await expect(page.locator('.hero').getByLabel('Descrição do caso')).toBeVisible()
    for (const sigla of ['STJ', 'TJDFT', 'BDJUR']) {
      await expect(fonte(page, sigla)).toBeChecked()
      await expect(fonte(page, sigla)).toBeEnabled()
    }

    printStep(cenario, 'Validando que nada foi listado nem consultado')
    await expect(page.getByRole('heading', { name: 'Resultados da pesquisa' })).toBeHidden()
    await expect(page.getByRole('region', { name: 'Busca e filtros' })).toBeHidden()
    await page.waitForTimeout(300)
    expect(consultas.documentos).toHaveLength(0)
    expect(consultas.dashboard).toHaveLength(0)
  })

  test('não deixa desmarcar a última fonte selecionada', async ({ page }) => {
    const cenario = 'última fonte'
    await mockarApi(page)
    await abrirFrontend(page)

    printStep(cenario, 'Desmarcando duas das três fontes')
    await fonte(page, 'STJ').uncheck()
    await fonte(page, 'TJDFT').uncheck()

    printStep(cenario, 'Validando a fonte restante travada')
    const ultima = fonte(page, 'BDJUR')
    await expect(ultima).toBeChecked()
    await expect(ultima).toBeDisabled()
    await expect(ultima).toHaveAccessibleDescription(
      'Ao menos uma fonte precisa ficar selecionada.',
    )
    await expect(page.getByText('Ao menos uma fonte precisa ficar selecionada.')).toBeVisible()
    await page.locator('.filtro-fontes .check.travada').click({ force: true })
    await expect(ultima).toBeChecked()

    printStep(cenario, 'Marcando outra fonte: a trava sai')
    await fonte(page, 'STJ').check()
    await expect(ultima).toBeEnabled()
    await expect(page.getByText('Ao menos uma fonte precisa ficar selecionada.')).toHaveCount(0)
  })

  test('pesquisa só nas fontes marcadas, inclusive várias', async ({ page }) => {
    const cenario = 'várias fontes'
    const consultas = await mockarApi(page)
    await abrirFrontend(page)

    printStep(cenario, 'Desmarcando BDJUR e pesquisando')
    await fonte(page, 'BDJUR').uncheck()
    await pesquisar(page)
    await expect(page.getByText('2 documentos encontrados', { exact: false })).toBeVisible()
    await expect(page.locator('article.card h3')).toHaveText(['Acórdão do STJ', 'Acórdão do TJDFT'])

    printStep(cenario, 'Validando as fontes enviadas à listagem e ao dashboard')
    expect(consultas.documentos).toHaveLength(3)
    for (const url of consultas.documentos) {
      expect(url.searchParams.getAll('fonte')).toEqual(['STJ', 'TJDFT'])
    }
    await expect.poll(() => consultas.dashboard.at(-1)?.searchParams.getAll('fonte'))
      .toEqual(['STJ', 'TJDFT'])
  })

  test('mudar as fontes só vale na próxima pesquisa', async ({ page }) => {
    const cenario = 'próxima pesquisa'
    const consultas = await mockarApi(page)
    await abrirFrontend(page)

    printStep(cenario, 'Pesquisando com todas as fontes')
    await pesquisar(page)
    await expect(page.getByText('3 documentos encontrados', { exact: false })).toBeVisible()
    expect(consultas.documentos.every((url) => !url.searchParams.has('fonte'))).toBe(true)

    printStep(cenario, 'Desmarcando uma fonte sem pesquisar')
    await fonte(page, 'STJ').uncheck()
    await page.waitForTimeout(300)
    expect(consultas.documentos).toHaveLength(3)
    await expect(page.getByText('3 documentos encontrados', { exact: false })).toBeVisible()

    printStep(cenario, 'Pesquisando de novo')
    await pesquisar(page)
    await expect(page.getByText('2 documentos encontrados', { exact: false })).toBeVisible()
    expect(consultas.documentos.at(-1)?.searchParams.getAll('fonte')).toEqual(['TJDFT', 'BDJUR'])
  })

  test('sem a lista de fontes, avisa e pesquisa em todas', async ({ page }) => {
    const cenario = 'falha nas fontes'
    const consultas = await mockarApi(page)
    await page.route('**/api/fontes', (route) => route.fulfill({ status: 500 }))
    await page.goto(FRONTEND_URL, { waitUntil: 'domcontentloaded' })

    printStep(cenario, 'Validando o aviso')
    await expect(page.getByRole('alert')).toHaveText(
      'Não foi possível carregar as fontes. A pesquisa vai considerar todas elas.',
    )

    printStep(cenario, 'Pesquisando sem filtro de fonte')
    await pesquisar(page)
    await expect(page.getByText('3 documentos encontrados', { exact: false })).toBeVisible()
    expect(consultas.documentos.every((url) => !url.searchParams.has('fonte'))).toBe(true)
  })
})
