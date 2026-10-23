import { expect, test, type Locator, type Page, type TestInfo } from '@playwright/test'
import { abrirFrontend } from './helpers/test-helper'

const decisoes = [
  {
    id: 1,
    fonte: 'TJDFT',
    categoria: 'decisoes',
    titulo: 'Decisão sobre dano moral',
    tribunal: 'TJDFT',
    orgaoJulgador: '1ª Turma Cível',
    dataPublicacao: '2025-04-10',
  },
  {
    id: 2,
    fonte: 'TJDFT',
    categoria: 'decisoes',
    titulo: 'Decisão sobre contrato',
    tribunal: 'TJDFT',
    orgaoJulgador: '2ª Turma Cível',
    dataPublicacao: '2024-04-10',
  },
]

const dashboardDanoMoral = {
  indicadores: { totalDecisoes: 6, classificadas: 6, favoraveis: 4, aderencia: 67 },
  distribuicaoMerito: [
    { categoria: 'Provido', quantidade: 4 },
    { categoria: 'Desprovido', quantidade: 2 },
  ],
  distribuicaoCamaraTurma: [
    { categoria: '1ª Turma Cível', quantidade: 4 },
    { categoria: '2ª Turma Cível', quantidade: 2 },
  ],
  distribuicaoTribunal: [{ categoria: 'TJDFT', quantidade: 6 }],
  evolucaoAnual: [
    { categoria: '2024', quantidade: 2 },
    { categoria: '2025', quantidade: 4 },
  ],
}

const dashboardContrato = {
  indicadores: { totalDecisoes: 2, classificadas: 2, favoraveis: 0, aderencia: 0 },
  distribuicaoMerito: [{ categoria: 'Desprovido', quantidade: 2 }],
  distribuicaoCamaraTurma: [{ categoria: '2ª Turma Cível', quantidade: 2 }],
  distribuicaoTribunal: [{ categoria: 'TJDFT', quantidade: 2 }],
  evolucaoAnual: [{ categoria: '2024', quantidade: 2 }],
}

async function etapa(nome: string, acao: () => Promise<void>): Promise<void> {
  console.info(`[SCRUM-193] ${nome}`)
  await test.step(nome, acao)
}

async function screenshot(
  alvo: Locator,
  testInfo: TestInfo,
  nome: string,
): Promise<void> {
  const caminho = testInfo.outputPath(`${nome}.png`)
  await alvo.screenshot({ path: caminho, animations: 'disabled' })
  await testInfo.attach(nome, { path: caminho, contentType: 'image/png' })
  console.info(`[SCRUM-193] Screenshot: ${caminho}`)
}

async function prepararApi(page: Page): Promise<string[]> {
  const consultas: string[] = []
  await page.route('**/api/documentos/**', async (route) => {
    const url = new URL(route.request().url())
    const termo = url.searchParams.get('termo') ?? ''
    const itens = url.pathname.endsWith('/decisoes')
      ? decisoes.filter((decisao) => !termo || decisao.titulo.toLowerCase().includes(termo))
      : []
    await route.fulfill({ json: {
      itens, pagina: 0, tamanho: 100, total: itens.length, totalPaginas: itens.length ? 1 : 0,
    } })
  })
  await page.route('**/api/dashboard?*', async (route) => {
    const url = new URL(route.request().url())
    consultas.push(url.searchParams.get('termo') ?? '')
    await route.fulfill({ json: url.searchParams.get('termo') === 'contrato'
      ? dashboardContrato : dashboardDanoMoral })
  })
  await abrirFrontend(page)
  // 'decisão' aparece no título das duas decisões do mock.
  await pesquisar(page, 'decisão')
  await expect(page.getByText('2 documentos encontrados', { exact: false })).toBeVisible()
  return consultas
}

function grafico(page: Page, titulo: string): Locator {
  return page.locator('.panorama .chart-container').filter({ has: page.getByRole('heading', { name: titulo }) })
}

async function pesquisar(page: Page, termo: string): Promise<void> {
  const buscaNaBarra = page.locator('.barra-fixa .busca-encaixe').getByLabel('Descrição do caso')
  const campo = await buscaNaBarra.count()
    ? buscaNaBarra
    : page.locator('.hero').getByLabel('Descrição do caso')
  await campo.fill(termo)
  await expect(campo).toHaveValue(termo)
  await campo.press('Enter')
}

test.describe('SCRUM-193 — dashboard da pesquisa', () => {
  test('CA01 — apresenta aderência e total de decisões analisadas', async ({ page }, testInfo) => {
    await etapa('Abrir pesquisa com resultados', async () => { await prepararApi(page) })
    const indicadores = page.getByRole('region', { name: 'Indicadores da pesquisa' })
    await etapa('Validar aderência e total analisado', async () => {
      const valores = indicadores.locator('.indicador-valor')
      await expect(valores.first()).toHaveText('67%')
      await expect(valores.last()).toHaveText('6')
      await expect(indicadores).toContainText('4 de 6 decisões favoráveis ao recurso')
      await screenshot(indicadores, testInfo, 'CA01-indicadores')
    })
  })

  test('CA02 — apresenta distribuição do mérito em barras', async ({ page }, testInfo) => {
    await etapa('Abrir pesquisa com mérito classificado', async () => { await prepararApi(page) })
    await etapa('Validar categorias e barras do gráfico', async () => {
      const merito = grafico(page, 'Resultado dos recursos')
      await expect(merito).toContainText('Provido')
      await expect(merito).toContainText('Desprovido')
      await expect(merito.locator('.recharts-bar-rectangle')).toHaveCount(2)
      await screenshot(merito, testInfo, 'CA02-distribuicao-merito')
    })
  })

  test('CA03 — apresenta volume por Câmara ou Turma em barras', async ({ page }, testInfo) => {
    await etapa('Abrir pesquisa com órgãos julgadores', async () => { await prepararApi(page) })
    await etapa('Validar categorias e barras do gráfico', async () => {
      const orgaos = grafico(page, 'Decisões por órgão julgador')
      await expect(orgaos).toContainText('1ª Turma Cível')
      await expect(orgaos).toContainText('2ª Turma Cível')
      await expect(orgaos.locator('.recharts-bar-rectangle')).toHaveCount(2)
      await screenshot(orgaos, testInfo, 'CA03-camaras-turmas')
    })
  })

  test('CA04 — apresenta volume anual de decisões em barras', async ({ page }, testInfo) => {
    await etapa('Abrir pesquisa com resultados de períodos distintos', async () => { await prepararApi(page) })
    await etapa('Validar anos e barras do gráfico', async () => {
      const evolucao = grafico(page, 'Evolução Temporal da Tese')
      await expect(evolucao).toContainText('2024')
      await expect(evolucao).toContainText('2025')
      await expect(evolucao.locator('.recharts-bar-rectangle')).toHaveCount(2)
      await screenshot(evolucao, testInfo, 'CA04-evolucao-temporal')
    })
  })

  test('CA05 — atualiza indicadores e gráficos conforme a pesquisa ativa', async ({ page }, testInfo) => {
    let consultas: string[] = []
    await etapa('Abrir a tela e preparar dois recortes da API', async () => {
      consultas = await prepararApi(page)
    })
    await etapa('Pesquisar dano moral e validar o primeiro recorte', async () => {
      await pesquisar(page, 'dano moral')
      await expect.poll(() => consultas.at(-1)).toBe('dano moral')
      await expect(page.getByText('1 documentos encontrados', { exact: false })).toBeVisible()
      await expect(page.getByRole('region', { name: 'Indicadores da pesquisa' })).toContainText('67%')
      await expect(grafico(page, 'Resultado dos recursos')).toContainText('Provido')
      await expect(grafico(page, 'Evolução Temporal da Tese')).toContainText('2025')
      await screenshot(page.locator('.panorama'), testInfo, 'CA05-dano-moral')
    })
    await etapa('Pesquisar contrato e validar atualização do dashboard', async () => {
      await pesquisar(page, 'contrato')
      await expect.poll(() => consultas.at(-1)).toBe('contrato')
      await expect(page.getByText('1 documentos encontrados', { exact: false })).toBeVisible()
      const indicadores = page.getByRole('region', { name: 'Indicadores da pesquisa' })
      await expect(indicadores).toContainText('0%')
      await expect(indicadores).toContainText('DECISÕES ANALISADAS')
      await expect(indicadores.locator('.indicador-valor').last()).toHaveText('2')
      await expect(grafico(page, 'Resultado dos recursos')).toContainText('Desprovido')
      await expect(grafico(page, 'Decisões por órgão julgador')).toContainText('2ª Turma Cível')
      await expect(grafico(page, 'Evolução Temporal da Tese')).toContainText('2024')
      expect(consultas).toContain('dano moral')
      expect(consultas.at(-1)).toBe('contrato')
      await screenshot(page.locator('.panorama'), testInfo, 'CA05-contrato')
    })
  })
})
