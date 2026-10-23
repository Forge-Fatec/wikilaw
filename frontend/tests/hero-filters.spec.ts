import { expect, test, type Page } from '@playwright/test'
import { abrirFrontend, aguardarRolagem, pesquisar } from './helpers/test-helper'

const ITENS = Array.from({ length: 10 }, (_, index) => ({
  id: index + 1,
  fonte: 'STJ',
  categoria: 'decisoes',
  titulo: `Documento ${index + 1}`,
  resumoOuEmenta: 'Ementa de teste. '.repeat(20),
  tribunal: 'STJ',
  dataPublicacao: `2024-01-${String(index + 1).padStart(2, '0')}`,
}))

function printStep(cenario: string, etapa: string): void {
  console.info(`[filtros no hero][${cenario}] ${etapa}`)
}

async function mockarApi(page: Page) {
  await page.route('**/api/documentos/**', (route) => route.fulfill({
    json: {
      itens: route.request().url().includes('/decisoes') ? ITENS : [],
      pagina: 0, tamanho: 100, total: ITENS.length, totalPaginas: 1,
    },
  }))
  await page.route('**/api/dashboard?*', (route) => route.fulfill({ status: 500 }))
}

function botaoDoHero(page: Page) {
  return page.locator('.filtros-hero .filters-toggle')
}

function alturaDoPainel(page: Page) {
  return page.locator('#painel-filtros').evaluate((el) => el.getBoundingClientRect().height)
}

// Alturas do painel quadro a quadro, a partir do clique no botão do hero.
async function alturasAoAbrir(page: Page): Promise<number[]> {
  await expect(botaoDoHero(page)).toBeVisible()
  return page.evaluate(async () => {
    const painel = document.querySelector('#painel-filtros')!
    const alturas: number[] = []
    ;(document.querySelector('.filtros-hero .filters-toggle') as HTMLButtonElement).click()
    const fim = performance.now() + 600
    while (performance.now() < fim) {
      await new Promise((pronto) => requestAnimationFrame(pronto))
      alturas.push(Math.round(painel.getBoundingClientRect().height))
    }
    return alturas
  })
}

test.describe('filtros abaixo da busca do hero', () => {
  test('botão centralizado abaixo da busca abre e fecha o painel com todos os filtros', async ({ page }) => {
    const cenario = 'abrir e fechar'
    await abrirFrontend(page)

    printStep(cenario, 'Validando o botão centralizado abaixo da busca')
    const busca = (await page.locator('.hero .search-form').boundingBox())!
    const botao = (await botaoDoHero(page).boundingBox())!
    expect(Math.abs(botao.x + botao.width / 2 - (busca.x + busca.width / 2))).toBeLessThanOrEqual(1)
    expect(botao.y).toBeGreaterThan(busca.y + busca.height)

    printStep(cenario, 'Painel começa recolhido e fora do foco')
    await expect(botaoDoHero(page)).toHaveAttribute('aria-expanded', 'false')
    expect(await alturaDoPainel(page)).toBeLessThan(1)
    await expect(page.locator('.painel-filtros-conteudo')).toHaveAttribute('inert', '')

    printStep(cenario, 'Abrindo: fontes, tipo, tribunal e período aparecem')
    await botaoDoHero(page).click()
    await expect(botaoDoHero(page)).toHaveAttribute('aria-expanded', 'true')
    const painel = page.locator('#painel-filtros')
    await expect(painel.getByRole('group', { name: 'FONTES PESQUISADAS' })).toBeVisible()
    await expect(painel.getByRole('group', { name: 'TIPO DE DOCUMENTO' })).toBeVisible()
    await expect(painel.getByLabel('Tribunal')).toBeVisible()
    await expect(painel.getByRole('group', { name: 'PERÍODO' })).toBeVisible()
    await expect(painel.getByRole('checkbox', { name: 'STJ', exact: true })).toBeChecked()

    printStep(cenario, 'Fechando: o painel recolhe de novo')
    await botaoDoHero(page).click()
    await expect(botaoDoHero(page)).toHaveAttribute('aria-expanded', 'false')
    await expect.poll(() => alturaDoPainel(page)).toBeLessThan(1)
  })

  test('o painel se expande aos poucos', async ({ page }) => {
    await abrirFrontend(page)
    const alturas = await alturasAoAbrir(page)
    const finais = Math.max(...alturas)
    const intermediarias = alturas.filter((altura) => altura > 0 && altura < finais)
    expect(finais).toBeGreaterThan(100)
    // Sem animação não haveria nenhuma altura entre 0 e a final.
    expect(intermediarias.length).toBeGreaterThan(0)
  })

  test('com movimento reduzido, o painel abre de uma vez', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await abrirFrontend(page)
    const alturas = await alturasAoAbrir(page)
    expect(alturas[0]).toBe(Math.max(...alturas))
  })

  test('o botão da barra fixa sobe até o hero com o painel aberto', async ({ page }) => {
    const cenario = 'barra fixa'
    await mockarApi(page)
    await abrirFrontend(page)
    await pesquisar(page)
    await expect(page.getByText('10 documentos encontrados')).toBeVisible()
    await aguardarRolagem(page)
    expect(await page.evaluate(() => window.scrollY)).toBeGreaterThan(300)

    printStep(cenario, 'Clicando em Filtros na barra fixa')
    await page.getByRole('button', { name: 'Filtros: abrir o painel no topo da página' }).click()
    await expect(botaoDoHero(page)).toHaveAttribute('aria-expanded', 'true')
    await aguardarRolagem(page)
    await expect(page.locator('.hero .search-form')).toBeInViewport()
    await expect(page.locator('#painel-filtros').getByLabel('Tribunal')).toBeInViewport()

    printStep(cenario, 'Os filtros recortam a lista já carregada')
    await page.locator('#painel-filtros').getByLabel('Jurisprudência').uncheck()
    await expect(page.getByText('0 documentos encontrados')).toBeAttached()
  })
})
