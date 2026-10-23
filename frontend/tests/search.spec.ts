import { expect, test } from '@playwright/test'
import { abrirEPesquisar, abrirFrontend } from './helpers/test-helper'

const EMPTY_RESPONSE = {
  itens: [],
  pagina: 0,
  tamanho: 50,
  total: 0,
  totalPaginas: 0,
}

function printStep(cenario: string, etapa: string): void {
  console.info(`[busca][${cenario}] ${etapa}`)
}

test.describe('busca por conteúdos jurídicos', () => {
  test('ordena os resultados por data antes da paginação', async ({ page }) => {
    const itens = Array.from({ length: 11 }, (_, index) => ({
      id: index + 1,
      fonte: 'Fonte de teste',
      categoria: 'decisoes',
      titulo: `Documento ${index + 1}`,
      tipoDocumento: null,
      numeroProcessoOuTema: null,
      autoresOuRelator: null,
      resumoOuEmenta: null,
      decisao: null,
      tribunal: null,
      orgaoJulgador: null,
      dataJulgamento: null,
      dataPublicacao: `2024-01-${String(index + 1).padStart(2, '0')}`,
      dataOriginal: null,
      urlOriginal: null,
      idRegistroBruto: null,
    }))

    await page.route('**/api/documentos/**', async (route) => {
      const resposta = route.request().url().includes('/decisoes')
        ? { ...EMPTY_RESPONSE, itens, total: itens.length }
        : EMPTY_RESPONSE
      await route.fulfill({ json: resposta })
    })

    await abrirEPesquisar(page)
    await expect(page.getByText('11 documentos encontrados')).toBeVisible()
    await expect(page.locator('.card h3').first()).toHaveText('Documento 11')

    await page.getByRole('navigation', { name: 'Páginas de resultados' })
      .getByRole('button', { name: '2' }).click()
    await expect(page.locator('.card h3').first()).toHaveText('Documento 1')

    await page.getByRole('button', { name: 'Mais antigos' }).click()
    await expect(page.getByText('Página 1 de 2')).toBeVisible()
    await expect(page.locator('.card h3').first()).toHaveText('Documento 1')
    await expect(page.getByRole('button', { name: 'Mais antigos' }))
      .toHaveAttribute('aria-pressed', 'true')
  })

  test('inicia a busca ao enviar a descrição do caso', async ({ page }) => {
    const cenario = 'descrição preenchida'
    const requisicoes: string[] = []

    printStep(cenario, 'Configurando mock da API')
    await page.route('**/api/documentos/**', async (route) => {
      requisicoes.push(route.request().url())
      await route.fulfill({ json: EMPTY_RESPONSE })
    })

    printStep(cenario, 'Abrindo o frontend: nada é listado antes da busca')
    await abrirFrontend(page)
    await expect(page.getByRole('checkbox', { name: 'STJ' })).toBeChecked()
    expect(requisicoes).toHaveLength(0)

    printStep(cenario, 'Preenchendo a descrição do caso')
    const descricao = 'Dano moral por negativação indevida'
    await page
      .getByPlaceholder(/dano moral por negativação indevida/i)
      .fill(descricao)

    printStep(cenario, 'Enviando a busca')
    await page.getByRole('button', { name: /pesquisar/i }).click()

    printStep(cenario, 'Validando as requisições da busca')
    await expect.poll(() => requisicoes.length).toBe(3)
    for (const urlRequisitada of requisicoes) {
      const params = new URL(urlRequisitada).searchParams
      expect(params.get('termo')).toBe(descricao)
      // Todas as fontes marcadas: nenhuma é filtrada.
      expect(params.has('fonte')).toBe(false)
    }
  })

  test('ao rolar, a busca se encaixa na barra fixa e volta ao hero ao subir', async ({
    page,
  }) => {
    const cenario = 'encaixe pela rolagem'
    // Lista longa o bastante para a página rolar.
    const itens = Array.from({ length: 10 }, (_, index) => ({
      id: index + 1,
      fonte: 'Fonte de teste',
      categoria: 'decisoes',
      titulo: `Documento ${index + 1}`,
      tipoDocumento: null,
      numeroProcessoOuTema: null,
      autoresOuRelator: null,
      resumoOuEmenta: 'Ementa de teste. '.repeat(20),
      decisao: null,
      tribunal: null,
      orgaoJulgador: null,
      dataJulgamento: null,
      dataPublicacao: '2024-01-15',
      dataOriginal: null,
      urlOriginal: null,
      idRegistroBruto: null,
    }))

    printStep(cenario, 'Configurando mock da API')
    await page.route('**/api/documentos/**', (route) =>
      route.fulfill({
        json: route.request().url().includes('/decisoes')
          ? { ...EMPTY_RESPONSE, itens, total: itens.length, totalPaginas: 1 }
          : EMPTY_RESPONSE,
      }),
    )
    await page.route('**/api/dashboard*', (route) =>
      route.fulfill({ status: 500 }),
    )

    printStep(cenario, 'Abrindo o frontend e pesquisando: busca segue no hero')
    await abrirFrontend(page)
    const barra = page.getByRole('region', { name: 'Busca e filtros' })
    const buscaDoHero = page.locator('.hero').getByLabel('Descrição do caso')
    await buscaDoHero.fill('dano moral')
    await buscaDoHero.press('Enter')
    await expect(page.getByText('10 documentos encontrados')).toBeVisible()
    await expect(buscaDoHero).toBeInViewport()
    await expect(barra.getByLabel('Descrição do caso')).toHaveCount(0)

    printStep(cenario, 'Rolando para baixo: a busca entra na barra presa no topo')
    await page.mouse.wheel(0, 1500)
    await expect(barra.getByLabel('Descrição do caso')).toHaveValue('dano moral')
    await expect(barra.getByLabel('Descrição do caso')).toBeInViewport()
    await expect(page.locator('.barra-fixa')).toHaveAttribute('data-presa', '')
    await expect.poll(() => barra.evaluate((el) => el.getBoundingClientRect().top)).toBe(8)

    printStep(cenario, 'Rolando para cima: a busca volta ao hero')
    await page.mouse.wheel(0, -3000)
    await expect(barra.getByLabel('Descrição do caso')).toHaveCount(0)
    await expect(buscaDoHero).toBeInViewport()
    await expect(page.locator('.barra-fixa')).not.toHaveAttribute('data-presa', '')
  })

  test('nova pesquisa substitui a que ainda está em andamento', async ({ page }) => {
    const cenario = 'busca durante a carga'

    printStep(cenario, 'Configurando mock: primeira pesquisa que não termina')
    await page.route('**/api/documentos/**', async (route) => {
      if (new URL(route.request().url()).searchParams.get('termo') !== 'primeira') {
        await route.fulfill({ json: EMPTY_RESPONSE })
      }
      // A primeira pesquisa fica pendente.
    })
    await page.route('**/api/dashboard*', (route) =>
      route.fulfill({ status: 500 }),
    )

    printStep(cenario, 'Pesquisando antes da primeira pesquisa terminar')
    await abrirEPesquisar(page, 'primeira')
    await expect(page.locator('.results-count')).toHaveText('Buscando...')
    await page.getByLabel('Descrição do caso').fill('dano moral')
    await page.getByLabel('Descrição do caso').press('Enter')

    printStep(cenario, 'Validando que a pesquisa foi feita')
    await expect(page.getByText('0 documentos encontrados')).toBeVisible()
  })

  test('exibe erro e não inicia a busca com a descrição vazia', async ({
    page,
  }) => {
    const cenario = 'descrição vazia'
    const requisicoes: string[] = []

    printStep(cenario, 'Configurando mock da API')
    await page.route('**/api/documentos/**', async (route) => {
      requisicoes.push(route.request().url())
      await route.fulfill({ json: EMPTY_RESPONSE })
    })

    printStep(cenario, 'Abrindo o frontend')
    await abrirFrontend(page)

    printStep(cenario, 'Enviando a busca sem descrição')
    await page.getByRole('button', { name: /pesquisar/i }).click()

    printStep(cenario, 'Validando a mensagem de erro')
    await expect(page.getByRole('alert')).toHaveText(
      'Informe a descrição do caso para realizar a pesquisa.',
    )

    printStep(cenario, 'Validando que nenhuma busca foi iniciada')
    await page.waitForTimeout(200)
    expect(requisicoes).toHaveLength(0)
  })
})
