import { expect, test, type Page } from '@playwright/test'
import { abrirFrontend } from './helpers/test-helper'

function printStep(cenario: string, etapa: string): void {
  console.info(`[filtro-graficos][${cenario}] ${etapa}`)
}

function item(
  id: number,
  categoria: string,
  resultado: string | null,
  orgaoJulgador: string | null,
) {
  return {
    id,
    fonte: 'TJDFT',
    categoria,
    titulo: `Documento ${id}`,
    tipoDocumento: null,
    numeroProcessoOuTema: null,
    autoresOuRelator: null,
    resumoOuEmenta: null,
    decisao: null,
    resultado,
    tribunal: 'TJDFT',
    orgaoJulgador,
    dataJulgamento: null,
    dataPublicacao: `2024-01-${String(id).padStart(2, '0')}`,
    dataOriginal: null,
    urlOriginal: null,
    idRegistroBruto: null,
  }
}

const DOCUMENTOS: Record<string, ReturnType<typeof item>[]> = {
  decisoes: [
    item(1, 'decisoes', 'Provido', '1ª Turma Cível'),
    item(2, 'decisoes', 'Desprovido', '1ª Turma Cível'),
    // Espaços extras: a tela normaliza o nome como o back faz no gráfico.
    item(3, 'decisoes', 'Provido', '  2ª   Turma Cível '),
    item(4, 'decisoes', 'Não identificado', null),
  ],
  precedentes: [item(5, 'precedentes', null, null)],
  doutrina: [],
}

// O gráfico em si não muda com a seleção no mock; o que importa é a tela
// repassar a seleção ao back e filtrar a lista.
const DASHBOARD = {
  indicadores: { totalDecisoes: 4, classificadas: 3, favoraveis: 2, aderencia: 67 },
  distribuicaoMerito: [
    { categoria: 'Provido', quantidade: 2 },
    { categoria: 'Parcialmente provido', quantidade: 0 },
    { categoria: 'Desprovido', quantidade: 1 },
    { categoria: 'Não identificado', quantidade: 1 },
  ],
  distribuicaoCamaraTurma: [
    { categoria: '1ª Turma Cível', quantidade: 2 },
    { categoria: '2ª Turma Cível', quantidade: 1 },
    { categoria: 'Outros', quantidade: 1 },
  ],
}

async function mockarApi(page: Page) {
  const consultas: URLSearchParams[] = []

  await page.route('**/api/documentos/**', async (route) => {
    const categoria = new URL(route.request().url()).pathname.split('/').at(-1) ?? ''
    const itens = DOCUMENTOS[categoria] ?? []
    await route.fulfill({
      json: { itens, pagina: 0, tamanho: 100, total: itens.length, totalPaginas: 1 },
    })
  })
  await page.route('**/api/dashboard*', async (route) => {
    consultas.push(new URL(route.request().url()).searchParams)
    await route.fulfill({ json: DASHBOARD })
  })

  return consultas
}

function grafico(page: Page, titulo: string) {
  return page.locator('.chart-container').filter({ hasText: titulo })
}

// O gráfico de órgão julgador tem barras deitadas: as categorias ficam no eixo Y.
const EIXO_DAS_CATEGORIAS: Record<string, 'x' | 'y'> = {
  'Resultado dos recursos': 'x',
  'Decisões por órgão julgador': 'y',
}

// Clica na faixa da categoria (posição do tick no eixo das categorias), no
// meio da área do gráfico: o índice segue a ordem dos dados, mesmo de barras com zero.
async function clicarNaBarra(page: Page, titulo: string, indice: number) {
  const container = grafico(page, titulo)
  const eixo = EIXO_DAS_CATEGORIAS[titulo]
  const superficie = container.locator('svg.recharts-surface')
  const [svg, tick, area] = await Promise.all([
    superficie.boundingBox(),
    container
      .locator(`.recharts-${eixo}Axis .recharts-cartesian-axis-tick-line`)
      .nth(indice)
      .boundingBox(),
    container.locator('.recharts-cartesian-grid').boundingBox(),
  ])
  if (!svg || !tick || !area) throw new Error(`Gráfico "${titulo}" não renderizado`)
  // Posição relativa ao SVG, que não muda quando o Playwright rola até ele.
  const [x, y] =
    eixo === 'x'
      ? [tick.x + tick.width / 2, area.y + area.height / 2]
      : [area.x + area.width / 2, tick.y + tick.height / 2]
  const position = { x: x - svg.x, y: y - svg.y }
  // O Recharts só sabe a categoria sob o mouse no quadro seguinte ao hover;
  // o tooltip aparecer é o sinal de que o clique já vai ser reconhecido.
  await superficie.hover({ position })
  await expect(container.locator('.recharts-tooltip-wrapper')).toBeVisible()
  await superficie.click({ position })
}

function titulosListados(page: Page) {
  return page.locator('article.card h3')
}

test.describe('filtro pela seleção nos gráficos', () => {
  test('clicar numa barra filtra a lista e o dashboard', async ({ page }) => {
    const cenario = 'seleção de resultado'

    printStep(cenario, 'Configurando mock da API')
    const consultas = await mockarApi(page)

    printStep(cenario, 'Abrindo o frontend')
    await abrirFrontend(page)
    await expect(page.getByText('5 documentos encontrados')).toBeVisible()

    printStep(cenario, 'Selecionando "Provido"')
    await clicarNaBarra(page, 'Resultado dos recursos', 0)

    printStep(cenario, 'Validando lista, chip e consulta ao back')
    await expect(titulosListados(page)).toHaveText(['Documento 3', 'Documento 1'])
    await expect(
      page.getByRole('button', { name: 'Remover filtro Provido' }),
    ).toBeVisible()
    await expect.poll(() => consultas.at(-1)?.get('resultado')).toBe('Provido')

    printStep(cenario, 'Removendo pelo chip')
    await page.getByRole('button', { name: 'Remover filtro Provido' }).click()
    await expect(page.getByText('5 documentos encontrados')).toBeVisible()
    await expect.poll(() => consultas.at(-1)?.has('resultado')).toBe(false)
  })

  test('combina as seleções dos dois gráficos', async ({ page }) => {
    const cenario = 'seleção combinada'

    printStep(cenario, 'Configurando mock da API')
    const consultas = await mockarApi(page)
    await abrirFrontend(page)
    await expect(page.getByText('5 documentos encontrados')).toBeVisible()

    printStep(cenario, 'Selecionando "Provido" e "2ª Turma Cível"')
    await clicarNaBarra(page, 'Resultado dos recursos', 0)
    await expect(titulosListados(page)).toHaveCount(2)
    await clicarNaBarra(page, 'Decisões por órgão julgador', 1)

    printStep(cenario, 'Validando o recorte')
    await expect(titulosListados(page)).toHaveText(['Documento 3'])
    await expect
      .poll(() => consultas.at(-1)?.toString())
      .toBe('resultado=Provido&orgaoJulgador=2%C2%AA+Turma+C%C3%ADvel')

    printStep(cenario, 'Clicando de novo na barra selecionada')
    await clicarNaBarra(page, 'Decisões por órgão julgador', 1)
    await expect(titulosListados(page)).toHaveCount(2)
  })

  test('"Outros" não é selecionável e limpar filtros desfaz a seleção', async ({
    page,
  }) => {
    const cenario = 'outros e limpar'

    printStep(cenario, 'Configurando mock da API')
    await mockarApi(page)
    await abrirFrontend(page)
    await expect(page.getByText('5 documentos encontrados')).toBeVisible()

    printStep(cenario, 'Clicando em "Outros"')
    await clicarNaBarra(page, 'Decisões por órgão julgador', 2)
    await expect(page.locator('.chart-selecao')).toHaveCount(0)
    await expect(page.getByText('5 documentos encontrados')).toBeVisible()

    printStep(cenario, 'Clicando na barra vazia de "Parcialmente provido"')
    await clicarNaBarra(page, 'Resultado dos recursos', 1)
    await expect(page.locator('.chart-selecao')).toHaveCount(0)

    printStep(cenario, 'Selecionando "Desprovido" e limpando os filtros')
    await clicarNaBarra(page, 'Resultado dos recursos', 2)
    await expect(titulosListados(page)).toHaveText(['Documento 2'])
    await page.getByRole('button', { name: 'Limpar filtros' }).click()
    await expect(page.locator('.chart-selecao')).toHaveCount(0)
    await expect(page.getByText('5 documentos encontrados')).toBeVisible()
  })
})

test.describe('painel de filtros recolhível', () => {
  test('começa fechado e resume os filtros ativos em chips', async ({ page }) => {
    const cenario = 'painel e chips'

    printStep(cenario, 'Configurando mock da API')
    await mockarApi(page)
    await abrirFrontend(page)
    await expect(page.getByText('5 documentos encontrados')).toBeVisible()

    const botao = page.getByRole('button', { name: 'Filtros', exact: false, expanded: false })
    printStep(cenario, 'Validando o painel fechado')
    await expect(botao).toHaveAttribute('aria-expanded', 'false')
    await expect(page.locator('#painel-filtros')).toHaveCount(0)

    printStep(cenario, 'Abrindo o painel e filtrando por tipo')
    await botao.click()
    await expect(page.getByLabel('Fonte')).toBeVisible()
    await page.getByLabel('Doutrina').uncheck()
    await clicarNaBarra(page, 'Resultado dos recursos', 0)

    printStep(cenario, 'Validando os chips e o contador')
    const ativos = page.getByRole('list', { name: 'Filtros ativos' })
    await expect(ativos.getByRole('button')).toHaveText([
      'Tipo: Jurisprudência, Precedente ×',
      'Resultado: Provido ×',
    ])
    await expect(page.locator('.filters-count')).toHaveText('2')

    printStep(cenario, 'Fechando o painel: os chips continuam')
    await page.getByRole('button', { name: 'Filtros', expanded: true }).click()
    await expect(page.locator('#painel-filtros')).toHaveCount(0)
    await ativos.getByRole('button', { name: 'Remover filtro Resultado: Provido' }).click()
    await expect(ativos.getByRole('button')).toHaveText(['Tipo: Jurisprudência, Precedente ×'])
    // O mock não tem doutrina: sem o filtro do gráfico voltam as 4 decisões e o precedente.
    await expect(page.getByText('5 documentos encontrados')).toBeVisible()
  })
})
