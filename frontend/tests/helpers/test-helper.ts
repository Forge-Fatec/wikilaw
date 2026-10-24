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

export const USUARIO_PADRAO = {
  id: 1,
  login: 'Admin',
  perfil: 'ADMIN',
  ativo: true,
  criadoEm: '2026-01-01T00:00:00Z',
}

const SENHA_ADMIN = 'Admin 123'

// Sem back no CI: o formulário de login usa respostas simuladas da API.
export async function simularSessao(page: Page): Promise<void> {
  // Testes sem mock próprio de dashboard ainda fazem essa consulta após a busca.
  // A rota no contexto é substituída pelas rotas específicas registradas na página.
  await page.context().route('**/api/dashboard?*', (route) => route.fulfill({ json: {
    indicadores: { totalDecisoes: 0, classificadas: 0, favoraveis: 0, aderencia: null },
    distribuicaoMerito: [],
    distribuicaoCamaraTurma: [],
    distribuicaoTribunal: [],
    evolucaoAnual: [],
  } }))
  await page.route('**/api/auth/me', (route) => route.fulfill({ status: 401 }))
  await page.route('**/api/auth/csrf', (route) => route.fulfill({ json: { token: 'csrf-teste' } }))
  await page.route('**/api/auth/login', async (route) => {
    const { login, senha } = route.request().postDataJSON() as { login: string; senha: string }
    if (login !== USUARIO_PADRAO.login || senha !== SENHA_ADMIN) {
      await route.fulfill({ status: 401, json: { detail: 'Login ou senha inválidos.' } })
      return
    }
    await route.fulfill({ json: USUARIO_PADRAO })
  })
}

export async function entrarComoAdmin(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: 'Bem-vindo de volta.' })).toBeVisible()
  await page.getByLabel('Login', { exact: true }).fill(USUARIO_PADRAO.login)
  await page.getByLabel('Senha').fill(SENHA_ADMIN)
  await page.getByRole('button', { name: 'ENTRAR' }).click()
  await expect(page.getByRole('heading', { name: 'Bem-vindo de volta.' })).toHaveCount(0)
}

// A lista de fontes vem sempre do mock: o teste não depende do cadastro do back.
export async function abrirFrontend(
  page: Page,
  fontes: FonteMock[] = FONTES_PADRAO,
): Promise<void> {
  await simularSessao(page)
  await page.route('**/api/fontes', (route) => route.fulfill({ json: fontes }))
  await page.goto(FRONTEND_URL, { waitUntil: 'domcontentloaded' })
  await entrarComoAdmin(page)
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

// O painel de filtros (fontes, tipo, tribunal e período) fica recolhido
// abaixo da busca do hero; o botão centralizado o expande.
export async function abrirFiltros(page: Page): Promise<void> {
  await aguardarRolagem(page)
  const botao = page.locator('.filtros-hero .filters-toggle')
  if ((await botao.getAttribute('aria-expanded')) !== 'true') await botao.click()
  await expect(page.locator('#painel-filtros')).toHaveAttribute('data-aberto')
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
