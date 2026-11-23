import { expect, test, type Response } from '@playwright/test'
import { pesquisar } from './helpers/test-helper'

const FRONTEND_URL = 'http://localhost:3000'
const USUARIO_INTEGRACAO = { login: 'Admin', senha: 'Admin 123' }

interface Documento {
  titulo: string
  resumoOuEmenta: string | null
  fonte: string | null
}

test('Sprint 1: pesquisar caso exibe título, resumo e fonte retornados pelo backend', async ({ page }) => {
  const { login, senha } = USUARIO_INTEGRACAO

  await test.step('Given: usuário autenticado na tela de pesquisa', async () => {
    await page.goto(FRONTEND_URL, { waitUntil: 'domcontentloaded' })
    const csrfUrl = await page.evaluate(async () => {
      const caminhoModulo = '/src/api.ts'
      const { API_BASE } = await import(caminhoModulo) as { API_BASE: string }
      return new URL(`${API_BASE}/api/auth/csrf`, window.location.href).href
    })
    const csrf = await page.request.get(csrfUrl, { timeout: 10_000 }).catch(() => null)
    test.skip(csrf === null, 'Backend indisponível: este cenário exige a API real ativa.')
    if (!csrf) return
    const corpoCsrf = await csrf.text()
    test.skip([502, 503, 504].includes(csrf.status()) || (csrf.status() === 500 && !corpoCsrf.trim()),
      'Backend indisponível no proxy do frontend; o CI atual inicia somente o Vite.')
    expect(csrf.ok(), `Inicialização CSRF respondeu HTTP ${csrf.status()}`).toBeTruthy()
    expect((JSON.parse(corpoCsrf) as { token?: string }).token, 'API deve fornecer o token CSRF').toBeTruthy()
    await page.getByLabel('Login', { exact: true }).fill(login)
    await page.getByLabel('Senha', { exact: true }).fill(senha)
    await page.getByRole('button', { name: 'ENTRAR', exact: true }).click()
    await expect(page.locator('.hero').getByLabel('Descrição do caso')
      .or(page.getByRole('alert'))).toBeVisible()
    await expect(page.getByRole('alert'), 'O login real deve concluir sem erro').toHaveCount(0)
    await expect(page.locator('.hero').getByLabel('Descrição do caso')).toBeVisible()
  })

  const respostas: Response[] = []
  const categorias = new Set<string>()
  page.on('response', response => {
    const url = new URL(response.url())
    const categoria = /^\/api\/documentos\/(decisoes|precedentes|doutrina)$/.exec(url.pathname)?.[1]
    if (!categoria || url.searchParams.get('termo') !== 'dano') return
    categorias.add(categoria)
    respostas.push(response)
  })

  await test.step('When: pesquisar uma descrição válida do caso', async () => {
    await pesquisar(page, 'dano')
    await expect(page.locator('article.card').first(), 'O banco deve conter documentos relacionados a dano').toBeVisible()
    await expect(page.locator('button[aria-busy="true"]')).toHaveCount(0)
  })

  await test.step('Then: cada resultado visível apresenta título, resumo e fonte da API', async () => {
    const documentos = (await Promise.all(respostas.map(async response => {
      expect(response.ok(), `API ${response.url()} respondeu HTTP ${response.status()}`).toBeTruthy()
      const dados = await response.json() as { itens: Documento[] }
      expect(Array.isArray(dados.itens)).toBeTruthy()
      return dados.itens
    }))).flat()
    expect([...categorias].sort()).toEqual(['decisoes', 'doutrina', 'precedentes'])
    expect(documentos.length).toBeGreaterThan(0)
    for (const card of await page.locator('article.card').all()) {
      const titulo = await card.locator('h3').innerText()
      const resumo = await card.locator('.ementa').innerText()
      const fonte = await card.locator('.proc-num').innerText()
      expect(documentos.some(item => item.titulo === titulo
        && (item.resumoOuEmenta ?? 'Sem resumo disponível.') === resumo
        && (item.fonte ?? 'Fonte não informada') === fonte),
      `Card deve corresponder a um documento retornado pela pesquisa: ${titulo}`).toBeTruthy()
      await expect(card.locator('h3')).toBeVisible()
      await expect(card.locator('.ementa')).toBeVisible()
      await expect(card.locator('.proc-num')).toBeVisible()
    }
  })
})
