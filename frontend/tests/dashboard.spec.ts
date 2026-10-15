import { expect, test, type Page } from '@playwright/test'
import { abrirFrontend } from './helpers/test-helper'

const EMPTY_RESPONSE = {
  itens: [],
  pagina: 0,
  tamanho: 100,
  total: 0,
  totalPaginas: 0,
}

function printStep(cenario: string, etapa: string): void {
  console.info(`[dashboard][${cenario}] ${etapa}`)
}

function item(id: number, categoria: string, tribunal: string) {
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
    tribunal,
    orgaoJulgador: null,
    dataJulgamento: null,
    dataPublicacao: `2024-01-${String(id).padStart(2, '0')}`,
    dataOriginal: null,
    urlOriginal: null,
    idRegistroBruto: null,
  }
}

const DECISOES = [
  item(1, 'decisoes', 'TJSP'),
  item(2, 'decisoes', 'TJRJ'),
]

function dashboard(
  totalDecisoes: number,
  classificadas: number,
  favoraveis: number,
  aderencia: number | null,
) {
  return {
    indicadores: { totalDecisoes, classificadas, favoraveis, aderencia },
    distribuicaoMerito: [
      { categoria: 'Provido', quantidade: favoraveis },
      { categoria: 'Parcialmente provido', quantidade: 0 },
      { categoria: 'Desprovido', quantidade: classificadas - favoraveis },
      { categoria: 'Não identificado', quantidade: totalDecisoes - classificadas },
    ],
    distribuicaoCamaraTurma: totalDecisoes
      ? [{ categoria: '1ª Turma Cível', quantidade: totalDecisoes }]
      : [],
  }
}

// O back responde por recorte; o mock devolve números diferentes por tribunal
// para provar que a tela repassa os filtros e exibe o que o back calculou.
const POR_TRIBUNAL: Record<string, ReturnType<typeof dashboard>> = {
  '': dashboard(6, 4, 2, 50),
  TJRJ: dashboard(2, 2, 0, 0),
  TJSP: dashboard(4, 2, 2, 100),
}

async function mockarApi(page: Page) {
  const consultas: URLSearchParams[] = []

  await page.route('**/api/documentos/**', async (route) => {
    const itens = route.request().url().includes('/decisoes') ? DECISOES : []
    await route.fulfill({
      json: { ...EMPTY_RESPONSE, itens, total: itens.length, totalPaginas: 1 },
    })
  })
  await page.route('**/api/dashboard*', async (route) => {
    const params = new URL(route.request().url()).searchParams
    consultas.push(params)
    await route.fulfill({ json: POR_TRIBUNAL[params.get('tribunal') ?? ''] })
  })

  return consultas
}

function indicador(page: Page, rotulo: string) {
  return page
    .getByRole('region', { name: 'Indicadores da pesquisa' })
    .locator('.indicador')
    .filter({ hasText: rotulo })
}

function valor(page: Page, rotulo: string) {
  return indicador(page, rotulo).locator('.indicador-valor')
}

function grafico(page: Page, titulo: string) {
  return page.locator('.chart-container').filter({ hasText: titulo })
}

test.describe('dashboard integrado à pesquisa ativa', () => {
  test('exibe os indicadores e gráficos calculados pelo back', async ({ page }) => {
    const cenario = 'carga inicial'

    printStep(cenario, 'Configurando mock da API')
    await mockarApi(page)

    printStep(cenario, 'Abrindo o frontend')
    await abrirFrontend(page)

    printStep(cenario, 'Validando os indicadores')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('50%')
    await expect(indicador(page, 'ADERÊNCIA')).toContainText(
      '2 de 4 decisões favoráveis ao recurso',
    )
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('6')
    await expect(indicador(page, 'DECISÕES ANALISADAS')).toContainText(
      '4 com resultado identificado',
    )

    printStep(cenario, 'Validando os gráficos')
    await expect(grafico(page, 'Resultado dos recursos')).toContainText('Desprovido')
    await expect(grafico(page, 'Decisões por órgão julgador')).toContainText(
      '1ª Turma Cível',
    )
  })

  test('envia o termo pesquisado ao dashboard', async ({ page }) => {
    const cenario = 'pesquisa por termo'

    printStep(cenario, 'Configurando mock da API')
    const consultas = await mockarApi(page)

    printStep(cenario, 'Pesquisando')
    await abrirFrontend(page)
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('50%')
    await page.getByLabel('Descrição do caso').fill('dano moral')
    await page.getByRole('button', { name: 'PESQUISAR' }).click()

    printStep(cenario, 'Validando a consulta enviada')
    await expect
      .poll(() => consultas.at(-1)?.get('termo'))
      .toBe('dano moral')
  })

  test('recalcula o dashboard ao aplicar filtros', async ({ page }) => {
    const cenario = 'filtro por tribunal'

    printStep(cenario, 'Configurando mock da API')
    const consultas = await mockarApi(page)

    printStep(cenario, 'Abrindo o frontend')
    await abrirFrontend(page)
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('50%')

    printStep(cenario, 'Filtrando por TJRJ')
    await page.locator('.filters select').nth(1).selectOption('TJRJ')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('0%')
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('2')

    printStep(cenario, 'Filtrando por TJSP e período')
    await page.locator('.filters select').nth(1).selectOption('TJSP')
    await page.locator('.date-input').first().fill('2024-01-01')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('100%')
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('4')
    await expect
      .poll(() => consultas.at(-1)?.toString())
      .toBe('tribunal=TJSP&dataDe=2024-01-01')
  })

  test('indica ausência de decisões sem consultar o back', async ({ page }) => {
    const cenario = 'sem jurisprudência'

    printStep(cenario, 'Configurando mock da API')
    const consultas = await mockarApi(page)

    printStep(cenario, 'Abrindo o frontend')
    await abrirFrontend(page)
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('50%')
    const consultasAntes = consultas.length

    printStep(cenario, 'Removendo jurisprudência dos filtros')
    await page.getByLabel('Jurisprudência').uncheck()

    printStep(cenario, 'Validando o dashboard vazio')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('—')
    await expect(indicador(page, 'ADERÊNCIA')).toContainText(
      'Nenhuma decisão com resultado identificado',
    )
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('0')
    await expect(grafico(page, 'Resultado dos recursos')).toContainText(
      'Nenhuma decisão na pesquisa atual.',
    )
    expect(consultas.length).toBe(consultasAntes)
  })

  test('mostra erro nos gráficos quando o dashboard falha', async ({ page }) => {
    const cenario = 'falha do dashboard'

    printStep(cenario, 'Configurando mock da API com falha')
    await mockarApi(page)
    await page.route('**/api/dashboard*', (route) => route.fulfill({ status: 500 }))

    printStep(cenario, 'Abrindo o frontend')
    await abrirFrontend(page)

    printStep(cenario, 'Validando a mensagem de erro')
    await expect(grafico(page, 'Resultado dos recursos')).toContainText(
      'Não foi possível carregar o dashboard da pesquisa.',
    )
    await expect(page.getByText('2 documentos encontrados')).toBeVisible()
  })
})
