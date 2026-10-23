import { expect, test, type Page } from '@playwright/test'
import { abrirFrontend, pesquisar } from './helpers/test-helper'

const ITENS = Array.from({ length: 10 }, (_, index) => ({
  id: index + 1,
  fonte: 'STJ',
  categoria: 'decisoes',
  titulo: `Documento ${index + 1}`,
  resumoOuEmenta: 'Ementa de teste. '.repeat(20),
  dataPublicacao: `2024-01-${String(index + 1).padStart(2, '0')}`,
}))

function printStep(cenario: string, etapa: string): void {
  console.info(`[tela inicial][${cenario}] ${etapa}`)
}

// A listagem só responde quando o teste libera, para observar o "PESQUISANDO".
async function mockarApi(page: Page, itens = ITENS) {
  let liberar: () => void = () => {}
  const liberada = new Promise<void>((resolve) => {
    liberar = resolve
  })
  await page.route('**/api/documentos/**', async (route) => {
    await liberada
    await route.fulfill({
      json: {
        itens: route.request().url().includes('/decisoes') ? itens : [],
        pagina: 0, tamanho: 100, total: itens.length, totalPaginas: 1,
      },
    })
  })
  await page.route('**/api/dashboard?*', (route) => route.fulfill({ status: 500 }))
  return { liberar }
}

// Distância entre o topo do alvo e a base da barra fixa.
function folgaAbaixoDaBarra(page: Page, seletor: string) {
  return page.evaluate((alvo) => {
    const barra = document.querySelector('.barra-fixa')!.getBoundingClientRect()
    const elemento = document.querySelector(alvo)
    return elemento ? Math.round(elemento.getBoundingClientRect().top - barra.bottom) : null
  }, seletor)
}

test.describe('tela inicial e rolagem até os resultados', () => {
  test('ao entrar, o hero ocupa a tela inteira e não há o que rolar', async ({ page }) => {
    const cenario = 'primeira tela'
    await abrirFrontend(page)

    printStep(cenario, 'Validando a altura da abertura e a cor do fundo')
    await expect(page.locator('.abertura')).toBeVisible()
    await expect(page.locator('.filtros-hero .filters-toggle')).toBeVisible()
    const medidas = await page.evaluate(() => ({
      abertura: document.querySelector('.abertura')!.getBoundingClientRect().height,
      janela: window.innerHeight,
      pagina: document.documentElement.scrollHeight,
      fundoHero: getComputedStyle(document.querySelector('.hero')!).backgroundColor,
    }))
    expect(medidas.abertura).toBe(medidas.janela)
    expect(medidas.pagina).toBe(medidas.janela)
    expect(medidas.fundoHero).toBe('rgb(30, 27, 48)')
  })

  test('mostra PESQUISANDO durante a pesquisa e volta para PESQUISAR', async ({ page }) => {
    const cenario = 'rótulo do botão'
    const api = await mockarApi(page)
    await abrirFrontend(page)

    printStep(cenario, 'Pesquisando com a API ainda sem responder')
    await pesquisar(page)
    const botao = page.locator('.hero button[type="submit"]')
    await expect(botao).toHaveText('PESQUISANDO...')
    await expect(botao).toHaveAttribute('aria-busy', 'true')

    printStep(cenario, 'Liberando a resposta')
    api.liberar()
    await expect(page.getByText('10 documentos encontrados')).toBeVisible()
    await expect(botao).toHaveText('PESQUISAR')
    await expect(botao).toHaveAttribute('aria-busy', 'false')
  })

  test('na primeira pesquisa a tela continua limpa até os resultados chegarem', async ({ page }) => {
    const cenario = 'primeira pesquisa'
    const api = await mockarApi(page)
    await abrirFrontend(page)

    printStep(cenario, 'Pesquisando: nada aparece abaixo do hero enquanto busca')
    await pesquisar(page)
    await expect(page.locator('.hero button[type="submit"]')).toHaveText('PESQUISANDO...')
    await expect(page.locator('.layout')).toBeHidden()
    expect(await page.evaluate(() => document.documentElement.scrollHeight === window.innerHeight))
      .toBe(true)

    printStep(cenario, 'Resultados chegam: a lista aparece')
    api.liberar()
    await expect(page.locator('.layout')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Resultados da pesquisa' })).toBeInViewport()
  })

  test('da segunda pesquisa em diante a lista anterior continua à vista', async ({ page }) => {
    const cenario = 'segunda pesquisa'
    // Uma promessa só para as três categorias da segunda pesquisa.
    let liberarSegunda: () => void = () => {}
    const segundaLiberada = new Promise<void>((resolve) => {
      liberarSegunda = resolve
    })
    await page.route('**/api/documentos/**', async (route) => {
      if (new URL(route.request().url()).searchParams.get('termo') === 'contrato') {
        await segundaLiberada
      }
      await route.fulfill({
        json: {
          itens: route.request().url().includes('/decisoes') ? ITENS : [],
          pagina: 0, tamanho: 100, total: ITENS.length, totalPaginas: 1,
        },
      })
    })
    await page.route('**/api/dashboard?*', (route) => route.fulfill({ status: 500 }))
    await abrirFrontend(page)
    await pesquisar(page)
    await expect(page.getByText('10 documentos encontrados')).toBeVisible()

    printStep(cenario, 'Pesquisando de novo: a lista segue visível durante a busca')
    await pesquisar(page, 'contrato')
    await expect(page.locator('.results-count')).toHaveText('Buscando...')
    await expect(page.locator('.layout')).toBeVisible()
    liberarSegunda()
    await expect(page.getByText('10 documentos encontrados')).toBeVisible()
  })

  test('depois de pesquisar, desce com suavidade até o título dos resultados', async ({ page }) => {
    const cenario = 'rolagem suave'
    const api = await mockarApi(page)
    await abrirFrontend(page)

    printStep(cenario, 'Pesquisando e registrando a rolagem quadro a quadro')
    await page.evaluate(() => {
      const posicoes: number[] = []
      ;(window as unknown as { posicoes: number[] }).posicoes = posicoes
      window.addEventListener('scroll', () => posicoes.push(window.scrollY))
    })
    await pesquisar(page)
    api.liberar()
    await expect(page.getByText('10 documentos encontrados')).toBeVisible()

    printStep(cenario, 'Validando que o título dos resultados para logo abaixo da barra')
    await expect.poll(() => folgaAbaixoDaBarra(page, '.results-header'), { timeout: 5_000 })
      .toBe(16)
    await expect(page.getByRole('heading', { name: 'Resultados da pesquisa' })).toBeInViewport()

    printStep(cenario, 'Validando que a descida passou por várias posições')
    const posicoes = await page.evaluate(
      () => (window as unknown as { posicoes: number[] }).posicoes,
    )
    // Um salto só daria 1 ou 2 posições; a animação passa por várias.
    expect(new Set(posicoes).size).toBeGreaterThan(2)
  })

  test('sem resultados, também para no título dos resultados', async ({ page }) => {
    const api = await mockarApi(page, [])
    await abrirFrontend(page)
    await pesquisar(page)
    api.liberar()
    // A página curta não deixa o título subir até a barra: rola o quanto dá.
    await expect(page.getByRole('heading', { name: 'Resultados da pesquisa' })).toBeInViewport()
    await expect(page.getByText('Nenhum documento encontrado com os filtros selecionados.'))
      .toBeInViewport()
  })

  test('com movimento reduzido, vai direto ao título dos resultados', async ({ page }) => {
    const cenario = 'movimento reduzido'
    await page.emulateMedia({ reducedMotion: 'reduce' })
    const api = await mockarApi(page)
    await abrirFrontend(page)

    printStep(cenario, 'Pesquisando e registrando a rolagem')
    await page.evaluate(() => {
      const posicoes: number[] = []
      ;(window as unknown as { posicoes: number[] }).posicoes = posicoes
      window.addEventListener('scroll', () => posicoes.push(window.scrollY))
    })
    await pesquisar(page)
    api.liberar()
    await expect.poll(() => folgaAbaixoDaBarra(page, '.results-header')).toBe(16)
    const posicoes = await page.evaluate(
      () => (window as unknown as { posicoes: number[] }).posicoes,
    )
    // Um salto só (a barra pode ajustar a posição uma vez ao receber a busca).
    expect(new Set(posicoes).size).toBeLessThanOrEqual(2)
  })
})
