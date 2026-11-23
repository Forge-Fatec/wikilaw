import { expect, test, type Page, type Response } from '@playwright/test'
import { pesquisar } from './helpers/test-helper'

const FRONTEND_URL = 'http://localhost:3000'
const USUARIO = { login: 'Admin', senha: 'Admin 123' }
const TERMO = 'dano'

interface Documento {
  id: number
  titulo: string
  categoria: string
  resultado: string | null
}

interface Dashboard {
  indicadores: { totalDecisoes: number; aderencia: number | null }
  distribuicaoMerito: Array<{ categoria: string; quantidade: number }>
}

function indicador(page: Page, rotulo: string) {
  return page.getByRole('region', { name: 'Indicadores da pesquisa' })
    .locator('.indicador').filter({ hasText: rotulo }).locator('.indicador-valor')
}

async function validarIndicadores(page: Page, dashboard: Dashboard) {
  await expect(indicador(page, 'DECISÕES ANALISADAS')).toHaveText(String(dashboard.indicadores.totalDecisoes))
  await expect(indicador(page, 'ADERÊNCIA')).toHaveText(dashboard.indicadores.aderencia === null
    ? '—' : `${dashboard.indicadores.aderencia}%`)
}

async function lerDashboard(resposta: Response): Promise<Dashboard> {
  expect(resposta.ok(), `Dashboard respondeu HTTP ${resposta.status()}`).toBeTruthy()
  return resposta.json() as Promise<Dashboard>
}

async function selecionarCategoria(page: Page, indice: number) {
  const grafico = page.locator('.chart-container').filter({
    has: page.getByRole('heading', { name: 'Resultado dos recursos', exact: true }),
  })
  const superficie = grafico.locator('svg.recharts-surface')
  let position = { x: 0, y: 0 }
  await expect(async () => {
    await grafico.evaluate(async el => {
      el.scrollIntoView({ block: 'center' })
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    })
    const [svg, tick, area] = await Promise.all([
      superficie.boundingBox(),
      grafico.locator('.recharts-xAxis .recharts-cartesian-axis-tick-line').nth(indice).boundingBox(),
      grafico.locator('.recharts-cartesian-grid').boundingBox(),
    ])
    if (!svg || !tick || !area) throw new Error('Gráfico de mérito ainda não está renderizado')
    position = { x: tick.x + tick.width / 2 - svg.x, y: area.y + area.height / 2 - svg.y }
    await superficie.hover({ position })
    await expect(grafico.locator('.recharts-tooltip-wrapper')).toBeVisible({ timeout: 1500 })
  }).toPass({ timeout: 15_000 })
  await superficie.click({ position })
}

test('Sprint 2 — US11/US12: selecionar categoria atualiza dashboard e resultados; remover restaura a pesquisa', async ({ page }) => {
  test.setTimeout(90_000)
  await test.step('Given: usuário autenticado com pesquisa de resultados de diferentes categorias', async () => {
    await page.goto(FRONTEND_URL, { waitUntil: 'domcontentloaded' })
    const csrfUrl = await page.evaluate(async () => {
      const caminhoModulo = '/src/api.ts'
      const { API_BASE } = await import(caminhoModulo) as { API_BASE: string }
      return new URL(`${API_BASE}/api/auth/csrf`, window.location.href).href
    })
    const csrf = await page.request.get(csrfUrl, { timeout: 10_000 }).catch(() => null)
    test.skip(csrf === null, 'Integração real exige backend ativo; o job padrão de Playwright inicia somente o Vite.')
    if (!csrf) return
    const corpo = await csrf.text()
    test.skip([502, 503, 504].includes(csrf.status()) || (csrf.status() === 500 && !corpo.trim()),
      'Backend indisponível: não é possível validar integração com o dashboard real.')
    expect(csrf.ok(), `CSRF respondeu HTTP ${csrf.status()}`).toBeTruthy()
    expect((JSON.parse(corpo) as { token?: string }).token).toBeTruthy()
    await page.getByLabel('Login', { exact: true }).fill(USUARIO.login)
    await page.getByLabel('Senha', { exact: true }).fill(USUARIO.senha)
    await page.getByRole('button', { name: 'ENTRAR', exact: true }).click()
    await expect(page.locator('.hero').getByLabel('Descrição do caso').or(page.getByRole('alert'))).toBeVisible()
    await expect(page.getByRole('alert'), 'Conta padrão deve estar ativa no ambiente de integração').toHaveCount(0)
    await expect(page.locator('.hero').getByLabel('Descrição do caso')).toBeVisible()
  })

  const respostasDocumentos: Response[] = []
  page.on('response', response => {
    const url = new URL(response.url())
    if (/^\/api\/documentos\/(decisoes|precedentes|doutrina)$/.test(url.pathname)
      && url.searchParams.get('termo') === TERMO) respostasDocumentos.push(response)
  })
  const aguardarDashboard = (categoria: string | null) => page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.pathname === '/api/dashboard' && url.searchParams.get('termo') === TERMO
      && url.searchParams.get('resultado') === categoria
  })

  const respostaInicial = aguardarDashboard(null)
  await pesquisar(page, TERMO)
  const inicial = await lerDashboard(await respostaInicial)
  await expect(page.locator('button[aria-busy="true"]')).toHaveCount(0)
  await expect(page.locator('article.card').first(), 'Cadastre decisões relacionadas a dano no ambiente de integração').toBeVisible()
  await validarIndicadores(page, inicial)
  const documentos = (await Promise.all(respostasDocumentos.map(async resposta => {
    expect(resposta.ok(), `Documentos responderam HTTP ${resposta.status()}`).toBeTruthy()
    return (await resposta.json() as { itens: Documento[] }).itens
  }))).flat()
  const categoriasPositivas = inicial.distribuicaoMerito.filter(item => item.quantidade > 0)
  expect(categoriasPositivas.length, 'A pesquisa de integração deve conter pelo menos dois resultados de mérito distintos').toBeGreaterThanOrEqual(2)
  const categoria = categoriasPositivas[0].categoria
  const selecionados = documentos.filter(item => item.categoria === 'decisoes' && item.resultado === categoria)
  const totalOriginal = documentos.length
  const titulosOriginais = await page.locator('article.card h3').allTextContents()
  await expect(page.locator('.results-count')).toContainText(`${totalOriginal} documentos encontrados`)

  await test.step('When: selecionar uma categoria no gráfico de resultado dos recursos', async () => {
    const respostaFiltrada = aguardarDashboard(categoria)
    await selecionarCategoria(page, inicial.distribuicaoMerito.findIndex(item => item.categoria === categoria))
    const filtrado = await lerDashboard(await respostaFiltrada)
    await expect(page.getByRole('button', { name: `Remover filtro ${categoria}`, exact: true })).toBeVisible()
    await validarIndicadores(page, filtrado)
    expect(filtrado.indicadores.totalDecisoes).toBe(selecionados.length)
    expect(filtrado.indicadores.totalDecisoes).toBeLessThan(inicial.indicadores.totalDecisoes)
  })

  await test.step('Then: indicadores e lista representam apenas a categoria selecionada', async () => {
    await expect(page.locator('.results-count')).toContainText(`${selecionados.length} documentos encontrados`)
    await expect(page.locator('article.card')).toHaveCount(Math.min(10, selecionados.length))
    for (const titulo of await page.locator('article.card h3').allTextContents()) {
      expect(selecionados.some(item => item.titulo === titulo), `Resultado deve pertencer a ${categoria}: ${titulo}`).toBeTruthy()
    }
  })

  await test.step('And: remover o filtro restaura indicadores e resultados da pesquisa', async () => {
    const respostaRestaurada = aguardarDashboard(null)
    await page.getByRole('button', { name: `Remover filtro ${categoria}`, exact: true }).click()
    const restaurado = await lerDashboard(await respostaRestaurada)
    expect(restaurado).toEqual(inicial)
    await validarIndicadores(page, restaurado)
    await expect(page.getByRole('button', { name: `Remover filtro ${categoria}`, exact: true })).toHaveCount(0)
    await expect(page.locator('.results-count')).toContainText(`${totalOriginal} documentos encontrados`)
    await expect(page.locator('article.card h3')).toHaveText(titulosOriginais)
  })
})
