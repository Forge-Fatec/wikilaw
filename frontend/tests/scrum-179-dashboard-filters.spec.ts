import { expect, test, type Page, type TestInfo } from '@playwright/test'
import { abrirEPesquisar } from './helpers/test-helper'

type Decisao = { id: number; resultado: string; orgaoJulgador: string }

const DECISOES: Decisao[] = [
  { id: 1, resultado: 'Provido', orgaoJulgador: '1ª Turma Cível' },
  { id: 2, resultado: 'Desprovido', orgaoJulgador: '1ª Turma Cível' },
  { id: 3, resultado: 'Provido', orgaoJulgador: '2ª Turma Cível' },
  { id: 4, resultado: 'Desprovido', orgaoJulgador: '2ª Turma Cível' },
]

function documento(id: number, categoria: string, decisao?: Decisao) {
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
    resultado: decisao?.resultado ?? null,
    tribunal: 'TJDFT',
    orgaoJulgador: decisao?.orgaoJulgador ?? null,
    dataJulgamento: null,
    dataPublicacao: `2024-01-${String(id).padStart(2, '0')}`,
    dataOriginal: null,
    urlOriginal: null,
    idRegistroBruto: null,
  }
}

function dashboard(decisoes: Decisao[]) {
  const providas = decisoes.filter((item) => item.resultado === 'Provido').length
  return {
    indicadores: {
      totalDecisoes: decisoes.length,
      classificadas: decisoes.length,
      favoraveis: providas,
      aderencia: Math.round((providas / decisoes.length) * 100),
    },
    distribuicaoMerito: ['Provido', 'Parcialmente provido', 'Desprovido', 'Não identificado']
      .map((categoria) => ({
        categoria,
        quantidade: decisoes.filter((item) => item.resultado === categoria).length,
      })),
    distribuicaoCamaraTurma: ['1ª Turma Cível', '2ª Turma Cível']
      .map((categoria) => ({
        categoria,
        quantidade: decisoes.filter((item) => item.orgaoJulgador === categoria).length,
      }))
      .filter((item) => item.quantidade > 0),
    distribuicaoTribunal: [],
    evolucaoAnual: [],
  }
}

async function mockarApi(page: Page) {
  const consultas: string[] = []

  await page.route('**/api/documentos/**', async (route) => {
    const categoria = new URL(route.request().url()).pathname.split('/').at(-1)
    const itens = categoria === 'decisoes'
      ? DECISOES.map((item) => documento(item.id, 'decisoes', item))
      : categoria === 'precedentes' ? [documento(5, 'precedentes')] : []
    await route.fulfill({ json: {
      itens, pagina: 0, tamanho: 100, total: itens.length, totalPaginas: 1,
    } })
  })

  await page.route('**/api/dashboard*', async (route) => {
    const params = new URL(route.request().url()).searchParams
    consultas.push(params.toString())
    const decisoes = DECISOES.filter((item) =>
      (!params.get('resultado') || item.resultado === params.get('resultado')) &&
      (!params.get('orgaoJulgador') || item.orgaoJulgador === params.get('orgaoJulgador')),
    )
    await route.fulfill({ json: dashboard(decisoes) })
  })

  return consultas
}

function grafico(page: Page, titulo: string) {
  return page.locator('.chart-container').filter({ has: page.getByRole('heading', { name: titulo }) })
}

// Os eixos têm orientações diferentes; o tick localiza a faixa clicável de
// cada categoria mesmo quando a barra é curta.
async function clicarNaBarra(page: Page, titulo: string, indice: number) {
  const container = grafico(page, titulo)
  const eixo = titulo === 'Resultado dos recursos' ? 'x' : 'y'
  const superficie = container.locator('svg.recharts-surface')
  let position = { x: 0, y: 0 }

  await expect(async () => {
    await container.evaluate(async (el) => {
      el.scrollIntoView({ block: 'center' })
      await new Promise((pronto) => requestAnimationFrame(() => requestAnimationFrame(pronto)))
    })
    const [svg, tick, area] = await Promise.all([
      superficie.boundingBox(),
      container.locator(`.recharts-${eixo}Axis .recharts-cartesian-axis-tick-line`).nth(indice).boundingBox(),
      container.locator('.recharts-cartesian-grid').boundingBox(),
    ])
    if (!svg || !tick || !area) throw new Error(`Gráfico "${titulo}" não renderizado`)
    const [x, y] = eixo === 'x'
      ? [tick.x + tick.width / 2, area.y + area.height / 2]
      : [area.x + area.width / 2, tick.y + tick.height / 2]
    position = { x: x - svg.x, y: y - svg.y }
    await superficie.hover({ position, timeout: 3_000 })
    await expect(container.locator('.recharts-tooltip-wrapper')).toBeVisible({ timeout: 1_500 })
  }).toPass({ timeout: 20_000 })

  await superficie.click({ position })
}

function valor(page: Page, rotulo: string) {
  return page.getByRole('region', { name: 'Indicadores da pesquisa' })
    .locator('.indicador').filter({ hasText: rotulo }).locator('.indicador-valor')
}

async function etapa(nome: string, acao: () => Promise<void>) {
  await test.step(nome, async () => {
    console.info(`[SCRUM-179] ${nome}`)
    await acao()
  })
}

async function screenshot(page: Page, testInfo: TestInfo, nome: string) {
  await page.locator('.content').evaluate(async (el) => {
    el.querySelector('.panorama')?.scrollTo(0, 0)
    window.scrollTo(0, window.scrollY + el.getBoundingClientRect().top - 120)
    await new Promise((pronto) => requestAnimationFrame(() => requestAnimationFrame(pronto)))
  })
  await page.screenshot({ path: testInfo.outputPath(`${nome}.png`) })
}

test('SCRUM-179: gráficos filtram dashboard e resultados; limpar restaura a pesquisa', async ({ page }, testInfo) => {
  let consultas: string[] = []

  await etapa('Preparar a API e abrir a pesquisa completa', async () => {
    consultas = await mockarApi(page)
    await abrirEPesquisar(page)
    await expect(page.getByText('5 documentos encontrados')).toBeVisible()
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('4')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('50%')
    const orgaos = grafico(page, 'Decisões por órgão julgador')
    await orgaos.locator('.recharts-bar-rectangle').first().hover()
    await expect(orgaos.locator('.recharts-tooltip-item-value')).toHaveText('2')
    await screenshot(page, testInfo, '00-pesquisa-completa')
  })

  await etapa('CA01: selecionar a categoria de mérito Provido aplica o filtro', async () => {
    await clicarNaBarra(page, 'Resultado dos recursos', 0)
    await expect(page.getByRole('button', { name: 'Remover filtro Resultado: Provido' })).toBeVisible()
    await expect.poll(() => consultas.at(-1)).toBe('termo=dano+moral&resultado=Provido')
  })

  await etapa('CA02 e CA03: indicadores, gráficos e resultados refletem Provido', async () => {
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('2')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('100%')
    const orgaos = grafico(page, 'Decisões por órgão julgador')
    await expect(orgaos).toContainText('1ª Turma Cível')
    await expect(orgaos).toContainText('2ª Turma Cível')
    await orgaos.locator('.recharts-bar-rectangle').first().hover()
    await expect(orgaos.locator('.recharts-tooltip-item-value')).toHaveText('1')
    await expect(page.getByText('2 documentos encontrados')).toBeVisible()
    await expect(page.locator('article.card h3')).toHaveText(['Documento 3', 'Documento 1'])
    await screenshot(page, testInfo, '01-filtrado-por-merito')
  })

  await etapa('CA01: selecionar 2ª Turma Cível combina os filtros dos gráficos', async () => {
    await clicarNaBarra(page, 'Decisões por órgão julgador', 1)
    await expect(page.getByRole('button', { name: 'Remover filtro Órgão: 2ª Turma Cível' })).toBeVisible()
    await expect.poll(() => consultas.at(-1)).toBe('termo=dano+moral&resultado=Provido&orgaoJulgador=2%C2%AA+Turma+C%C3%ADvel')
  })

  await etapa('CA02 e CA03: dashboard e lista mostram apenas a decisão da turma selecionada', async () => {
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('1')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('100%')
    await expect(grafico(page, 'Decisões por órgão julgador')).toContainText('2ª Turma Cível')
    await expect(grafico(page, 'Decisões por órgão julgador')).not.toContainText('1ª Turma Cível')
    await expect(page.getByText('1 documentos encontrados')).toBeVisible()
    await expect(page.locator('article.card h3')).toHaveText(['Documento 3'])
    await screenshot(page, testInfo, '02-filtros-combinados')
  })

  await etapa('CA04: limpar filtros restaura indicadores, gráficos e resultados', async () => {
    await page.getByRole('button', { name: 'Limpar filtros' }).click()
    await expect.poll(() => consultas.at(-1)).toBe('termo=dano+moral')
    await expect(page.getByRole('list', { name: 'Filtros ativos' })).toHaveCount(0)
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('4')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('50%')
    await expect(grafico(page, 'Decisões por órgão julgador')).toContainText('1ª Turma Cível')
    await expect(grafico(page, 'Decisões por órgão julgador')).toContainText('2ª Turma Cível')
    const orgaos = grafico(page, 'Decisões por órgão julgador')
    await orgaos.locator('.recharts-bar-rectangle').first().hover()
    await expect(orgaos.locator('.recharts-tooltip-item-value')).toHaveText('2')
    await expect(page.getByText('5 documentos encontrados')).toBeVisible()
    await expect(page.locator('article.card h3')).toHaveText([
      'Documento 5', 'Documento 4', 'Documento 3', 'Documento 2', 'Documento 1',
    ])
    await screenshot(page, testInfo, '03-pesquisa-restaurada')
  })
})
