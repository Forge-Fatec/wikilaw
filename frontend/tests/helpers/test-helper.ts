import type { Page } from '@playwright/test'

export const FRONTEND_URL =
  process.env.FRONTEND_URL ?? 'http://localhost:3000'

export interface FonteMock {
  sigla: string
  nome: string
}

export const FONTES_PADRAO: FonteMock[] = [
  { sigla: 'STJ', nome: 'Superior Tribunal de Justiça' },
  { sigla: 'TJDFT', nome: 'Jurisprudência TJDFT' },
  { sigla: 'BDJUR', nome: 'Biblioteca Digital Jurídica do STJ' },
]

export const TERMO_PADRAO = 'dano moral'

// A lista de fontes vem sempre do mock: o teste não depende do cadastro do back.
export async function abrirFrontend(
  page: Page,
  fontes: FonteMock[] = FONTES_PADRAO,
): Promise<void> {
  await page.route('**/api/fontes', (route) => route.fulfill({ json: fontes }))
  await page.goto(FRONTEND_URL, { waitUntil: 'domcontentloaded' })
}

// Pesquisa pela busca do hero: a tela só lista documentos depois disso.
export async function pesquisar(page: Page, termo = TERMO_PADRAO): Promise<void> {
  const hero = page.locator('.hero')
  await hero.getByLabel('Descrição do caso').fill(termo)
  await hero.getByRole('button', { name: 'PESQUISAR' }).click()
}

export async function abrirEPesquisar(page: Page, termo = TERMO_PADRAO): Promise<void> {
  await abrirFrontend(page)
  await pesquisar(page, termo)
}
