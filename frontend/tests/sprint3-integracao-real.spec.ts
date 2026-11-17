import { expect, test, type Page } from '@playwright/test'
import { pesquisar } from './helpers/test-helper'

// Sprint 3: navegador e API reais, sem interceptações de rotas ou sessão simulada.
// Conta inicial padrão; backend e dados contendo "dano" são pré-requisitos.
// A análise requer o provedor de IA configurado no backend, não no .env do teste.
// US22 (chat sobre a análise) e US28 (alternância de tema) ainda não têm controles
// na aplicação: não são declaradas como funcionalidades cobertas por esta suíte.
const FRONTEND_URL = 'http://localhost:3000'
const USUARIO = { login: 'Admin', senha: 'Admin 123' }

async function entrar(page: Page) {
  await page.goto(FRONTEND_URL, { waitUntil: 'domcontentloaded' })
  const apiBase = await page.evaluate(async () => {
    const caminhoModulo = '/src/api.ts'
    const { API_BASE } = await import(caminhoModulo) as { API_BASE: string }
    return new URL(`${API_BASE}/api/`, window.location.href).href
  })
  const csrf = await page.request.get(`${apiBase}auth/csrf`, { timeout: 10_000 }).catch(() => null)
  test.skip(csrf === null, 'Backend real indisponível; o job padrão de Playwright inicia somente o Vite.')
  if (!csrf) return
  const corpo = await csrf.text()
  test.skip([502, 503, 504].includes(csrf.status()) || (csrf.status() === 500 && !corpo.trim()),
    'Este cenário de integração exige o backend real ativo.')
  expect(csrf.ok(), `CSRF respondeu HTTP ${csrf.status()}`).toBeTruthy()
  expect((JSON.parse(corpo) as { token?: string }).token).toBeTruthy()
  await page.getByLabel('Login', { exact: true }).fill(USUARIO.login)
  await page.getByLabel('Senha', { exact: true }).fill(USUARIO.senha)
  await page.getByRole('button', { name: 'ENTRAR', exact: true }).click()
  await expect(page.locator('.hero').getByLabel('Descrição do caso').or(page.getByRole('alert'))).toBeVisible()
  await expect(page.getByRole('alert'), 'A conta padrão deve estar ativa no ambiente de integração').toHaveCount(0)
  await expect(page.locator('.hero').getByLabel('Descrição do caso')).toBeVisible()
}

async function pesquisarComResultados(page: Page) {
  await pesquisar(page, 'dano')
  await expect(page.locator('article.card').first(), 'Cadastre documentos ativos contendo dano no ambiente de integração').toBeVisible()
  await expect(page.locator('button[aria-busy="true"]')).toHaveCount(0)
}

interface Fonte {
  numero: number
  categoria: string
  id: number
  titulo: string
  fonte: string | null
  identificadorExterno: string | null
  url: string | null
}

test('Sprint 3 — US19/US20/US21: consultar análise concluída e identificar os documentos que fundamentam suas fontes', async ({ page }) => {
  test.setTimeout(150_000)
  await test.step('Given: usuário autenticado com resultados relacionados ao caso', async () => {
    await entrar(page)
    await pesquisarComResultados(page)
  })
  const painel = page.getByRole('region', { name: 'Análise jurídica com IA' })
  const [resposta] = await Promise.all([
    page.waitForResponse(response => new URL(response.url()).pathname === '/api/pesquisas/analises'
      && response.request().method() === 'POST', { timeout: 120_000 }),
    painel.getByRole('button', { name: 'Analisar com IA', exact: true }).click(),
  ])
  // Sem provedor externo, registra ausência de infraestrutura sem simular análise.
  test.skip(resposta.status() === 503, 'Provedor de IA indisponível no backend; consulta real da análise não pode ser validada.')
  expect(resposta.ok(), `Análise respondeu HTTP ${resposta.status()}`).toBeTruthy()
  const analise = await resposta.json() as {
    resumo: string; geradaPorIa: boolean; fontes: Fonte[]
    resultadosUtilizados: Array<{ categoria: string; id: number }>
  }
  expect(analise.geradaPorIa).toBe(true)
  expect(analise.resumo.trim()).not.toBe('')
  expect(analise.fontes.length, 'O cenário exige análise com ao menos uma fonte verificável').toBeGreaterThan(0)

  await test.step('When: acessar o conteúdo da análise concluída', async () => {
    await expect(painel.locator('.ai-summary')).toContainText(analise.resumo)
    await expect(painel.getByRole('button', { name: 'Gerar novamente', exact: true })).toBeVisible()
  })

  await test.step('Then: fontes exibidas correspondem aos documentos utilizados na análise', async () => {
    const fontes = painel.getByRole('region', { name: 'Fontes utilizadas' })
    await expect(fontes.getByRole('listitem')).toHaveCount(analise.fontes.length)
    for (const fonte of analise.fontes) {
      expect(analise.resultadosUtilizados.some(ref => ref.id === fonte.id && ref.categoria === fonte.categoria)).toBeTruthy()
      const item = fontes.locator(`#fonte-analise-${fonte.numero}`)
      await expect(item).toContainText(`[${fonte.numero}]`)
      await expect(item.getByRole('button', { name: fonte.titulo, exact: true })).toBeVisible()
      if (fonte.fonte) await expect(item).toContainText(fonte.fonte)
      if (fonte.identificadorExterno) await expect(item).toContainText(fonte.identificadorExterno)
      if (fonte.url) await expect(item.getByRole('link', { name: /Abrir original/ })).toHaveAttribute('href', fonte.url)
      // Verifica também a identidade no acervo real, compartilhando a sessão do navegador.
      const endpoint = new URL(resposta.url())
      endpoint.pathname = `/api/documentos/${fonte.categoria}/${fonte.id}`
      const documento = await page.request.get(endpoint.href)
      expect(documento.ok()).toBeTruthy()
      const dados = await documento.json() as { titulo: string; fonte: string | null; identificadorExterno: string | null }
      expect(dados).toMatchObject({ titulo: fonte.titulo, fonte: fonte.fonte, identificadorExterno: fonte.identificadorExterno })
    }
    const primeira = analise.fontes[0]
    await fontes.locator(`#fonte-analise-${primeira.numero}`).getByRole('button', { name: primeira.titulo, exact: true }).click()
    await expect(page.getByRole('dialog')).toContainText(primeira.titulo)
  })
})

test('Sprint 3 — US23: caso sem acompanhamentos apresenta estado vazio sem erro', async ({ page }) => {
  await test.step('Given: usuário autenticado sem pesquisas salvas neste navegador', async () => { await entrar(page) })
  await test.step('When: abrir acompanhamentos', async () => {
    await page.getByRole('button', { name: /^Acompanhamentos/ }).click()
  })
  await test.step('Then: informar ausência de pesquisas acompanhadas', async () => {
    const dialogo = page.getByRole('dialog', { name: 'Pesquisas acompanhadas' })
    await expect(dialogo).toContainText('Nenhuma pesquisa acompanhada.')
    await expect(dialogo.locator('.acompanhamento')).toHaveCount(0)
  })
})

test('Sprint 3 — US23: acompanhamento salvo preserva identificação e origem da informação', async ({ page }) => {
  // Given: estado salvo no mecanismo real do produto (localStorage por login).
  // Não consulta novidades nem simula o endpoint semântico.
  await page.addInitScript(() => {
    localStorage.setItem('wikilaw:acompanhamentos:Admin', JSON.stringify([{
      id: 'acompanhamento-sprint3', descricao: 'Reparação por negativação indevida', fatos: null, documento: null,
      categorias: ['decisoes'], marco: { decisoes: 1, precedentes: 0, doutrina: 0 },
      criadaEm: '2026-11-01T12:00:00Z', verificadaEm: null, erro: null,
      novidades: [{ id: 1, categoria: 'decisoes', titulo: 'Decisão acompanhada de teste', fonte: 'TJDFT',
        tribunal: 'TJDFT', percentual: 80, secao: 'EMENTA', trecho: 'Negativação indevida.', justificativa: 'Contexto relacionado.' }],
    }]))
  })
  await entrar(page)
  await test.step('When: consultar o acompanhamento salvo', async () => {
    await page.getByRole('button', { name: /^Acompanhamentos/ }).click()
  })
  await test.step('Then: exibir pesquisa, identificação e fonte da informação acompanhada', async () => {
    const dialogo = page.getByRole('dialog', { name: 'Pesquisas acompanhadas' })
    await expect(dialogo).toContainText('Reparação por negativação indevida')
    await expect(dialogo).toContainText('Decisão acompanhada de teste')
    await expect(dialogo).toContainText('TJDFT')
    await expect(dialogo).toContainText('1 novo')
  })
})

test('Sprint 3 — US27: limpar filtros de categoria, tribunal e período restaura a pesquisa', async ({ page }) => {
  await entrar(page)
  await pesquisarComResultados(page)
  const originais = await page.locator('article.card h3').allTextContents()
  const contagemOriginal = await page.locator('.results-count').innerText()
  await expect(page.getByRole('button', { name: 'Limpar filtros', exact: true })).toHaveCount(0)

  await test.step('Given: pesquisa com filtros de categoria, tribunal e período', async () => {
    await page.getByRole('button', { name: 'Filtros: abrir o painel no topo da página', exact: true }).click()
    const painel = page.locator('#painel-filtros')
    await expect(painel).toHaveAttribute('data-aberto')
    const tipos = painel.getByRole('group', { name: 'TIPO DE DOCUMENTO' })
    await tipos.getByRole('checkbox', { name: 'Doutrina', exact: true }).uncheck()
    const tribunal = painel.getByRole('combobox', { name: /^tribunal$/i })
    await expect(tribunal).toBeVisible()
    const opcoes = await tribunal.locator('option').allTextContents()
    expect(opcoes.length, 'Os documentos da pesquisa devem possuir tribunal').toBeGreaterThan(1)
    await tribunal.selectOption(opcoes[1])
    await painel.getByLabel('De', { exact: true }).fill('2025-01-01')
    await painel.getByLabel('Até', { exact: true }).fill('2025-12-31')
    await expect(page.getByRole('list', { name: 'Filtros ativos' }).getByRole('listitem')).toHaveCount(4)
  })
  await test.step('When: limpar todos os filtros em uma única ação', async () => {
    await page.getByRole('button', { name: 'Limpar filtros', exact: true }).click()
  })
  await test.step('Then: restaurar os resultados originais sem filtros ativos', async () => {
    await expect(page.getByRole('list', { name: 'Filtros ativos' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Limpar filtros', exact: true })).toHaveCount(0)
    await expect(page.locator('.results-count')).toHaveText(contagemOriginal)
    await expect(page.locator('article.card h3')).toHaveText(originais)
    // Sem filtros ativos, a interface não oferece uma segunda limpeza que altere a pesquisa.
  })
})
