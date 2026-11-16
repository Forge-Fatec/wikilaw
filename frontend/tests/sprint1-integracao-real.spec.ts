import { expect, test } from '@playwright/test'
import { FRONTEND_URL, pesquisar } from './helpers/test-helper'

/**
 * Sprint 1 — Given: documentos ativos no banco e usuário autenticado.
 * When: envia descrição do caso. Then: exibe título, resumo e fonte reais.
 * Não intercepta APIs: frontend -> backend -> services -> repositories -> banco.
 * Requer WIKILAW_TEST_LOGIN e WIKILAW_TEST_PASSWORD e dados de integração
 * contendo pelo menos um documento com "dano" e campos de exibição completos.
 */
test('Sprint 1: pesquisar caso exibe título, resumo e fonte retornados pelo backend', async ({ page }) => {
  const login = process.env.WIKILAW_TEST_LOGIN
  const senha = process.env.WIKILAW_TEST_PASSWORD
  expect(login, 'Defina WIKILAW_TEST_LOGIN para o usuário de integração').toBeTruthy()
  expect(senha, 'Defina WIKILAW_TEST_PASSWORD para o usuário de integração').toBeTruthy()

  await page.goto(FRONTEND_URL, { waitUntil: 'domcontentloaded' })
  await page.getByLabel('Login', { exact: true }).fill(login!)
  await page.getByLabel('Senha').fill(senha!)
  await page.getByRole('button', { name: 'ENTRAR' }).click()
  await expect(page.getByRole('heading', { name: 'Bem-vindo de volta.' })).toHaveCount(0)

  const termo = 'dano'
  const categorias = ['decisoes', 'precedentes', 'doutrina'] as const
  const documentos: Array<{ titulo: string; resumoOuEmenta: string | null; fonte: string }> = []
  for (const categoria of categorias) {
    // page.request compartilha os cookies de autenticação do navegador.
    const resposta = await page.request.get(`${FRONTEND_URL}/api/documentos/${categoria}`, {
      params: { termo, tamanho: 50 },
    })
    expect(resposta.ok(), `API ${categoria} respondeu HTTP ${resposta.status()}`).toBeTruthy()
    const dados = await resposta.json() as {
      itens: Array<{ titulo: string; resumoOuEmenta: string | null; fonte: string }>
    }
    documentos.push(...dados.itens.filter(item => item.titulo && item.resumoOuEmenta && item.fonte))
  }
  expect(documentos.length, 'Cadastre um documento com dano, título, resumo e fonte no banco de integração').toBeGreaterThan(0)

  await pesquisar(page, termo)
  const documento = documentos[0]
  const card = page.locator('article.card').filter({ hasText: documento.titulo }).first()
  await expect(card).toBeVisible()
  await expect(card.locator('h3')).toHaveText(documento.titulo)
  await expect(card).toContainText(documento.resumoOuEmenta!)
  await expect(card).toContainText(documento.fonte)
})
