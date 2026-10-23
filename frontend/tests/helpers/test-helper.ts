import { expect, type Locator, type Page } from '@playwright/test'

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

// Espera a rolagem automática até a lista terminar (posição estável).
export async function aguardarRolagem(page: Page): Promise<void> {
  await expect
    .poll(async () => {
      const antes = await page.evaluate(() => window.scrollY)
      await page.waitForTimeout(150)
      return (await page.evaluate(() => window.scrollY)) === antes
    }, { timeout: 5_000 })
    .toBe(true)
}

// Depois de uma pesquisa a tela desce até a lista e a busca passa para a
// barra fixa; antes disso, só existe a do hero.
export async function formularioDeBusca(page: Page): Promise<Locator> {
  await aguardarRolagem(page)
  const naBarra = page.locator('.barra-fixa .busca-encaixe[data-fase="barra"]')
  return (await naBarra.count()) ? naBarra : page.locator('.hero')
}

export async function campoDeBusca(page: Page): Promise<Locator> {
  return (await formularioDeBusca(page)).getByLabel('Descrição do caso')
}

// A tela só lista documentos depois de uma pesquisa.
export async function pesquisar(page: Page, termo = TERMO_PADRAO): Promise<void> {
  const formulario = await formularioDeBusca(page)
  await formulario.getByLabel('Descrição do caso').fill(termo)
  await formulario.getByRole('button', { name: 'PESQUISAR' }).click()
}

export async function abrirEPesquisar(page: Page, termo = TERMO_PADRAO): Promise<void> {
  await abrirFrontend(page)
  await pesquisar(page, termo)
}
