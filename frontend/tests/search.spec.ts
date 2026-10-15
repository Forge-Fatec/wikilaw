import { expect, test } from '@playwright/test'
import { abrirFrontend } from './helpers/test-helper'

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

    await abrirFrontend(page)
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

    printStep(cenario, 'Abrindo o frontend')
    await abrirFrontend(page)
    await expect(page.locator('.results-count')).not.toHaveText('Buscando...')
    const totalAposCarregamentoInicial = requisicoes.length

    printStep(cenario, 'Preenchendo a descrição do caso')
    const descricao = 'Dano moral por negativação indevida'
    await page
      .getByPlaceholder(/dano moral por negativação indevida/i)
      .fill(descricao)

    printStep(cenario, 'Enviando a busca')
    await page.getByRole('button', { name: /pesquisar/i }).click()

    printStep(cenario, 'Validando as requisições da busca')
    await expect
      .poll(() => requisicoes.length)
      .toBe(totalAposCarregamentoInicial + 3)

    const requisicoesDaBusca = requisicoes.slice(totalAposCarregamentoInicial)
    expect(requisicoesDaBusca).toHaveLength(3)
    for (const urlRequisitada of requisicoesDaBusca) {
      expect(new URL(urlRequisitada).searchParams.get('termo')).toBe(descricao)
    }
  })

  test('após pesquisar, a busca passa para a barra de filtros fixa no topo', async ({
    page,
  }) => {
    const cenario = 'busca na barra'

    printStep(cenario, 'Configurando mock da API')
    await page.route('**/api/documentos/**', (route) =>
      route.fulfill({ json: EMPTY_RESPONSE }),
    )
    await page.route('**/api/dashboard*', (route) =>
      route.fulfill({ status: 500 }),
    )

    printStep(cenario, 'Abrindo o frontend: busca em destaque')
    await abrirFrontend(page)
    const barra = page.getByRole('region', { name: 'Busca e filtros' })
    await expect(page.locator('.hero').getByLabel('Descrição do caso')).toBeVisible()
    await expect(barra.getByLabel('Descrição do caso')).toHaveCount(0)

    printStep(cenario, 'Pesquisando')
    await page.getByLabel('Descrição do caso').fill('dano moral')
    await page.getByRole('button', { name: /pesquisar/i }).click()

    printStep(cenario, 'Validando a busca dentro da barra fixa')
    await expect(page.locator('.hero')).toHaveCount(0)
    await expect(barra.getByLabel('Descrição do caso')).toHaveValue('dano moral')
    await expect(barra).toHaveCSS('position', 'sticky')

    printStep(cenario, 'Rolando a página: a barra continua visível')
    await page.mouse.wheel(0, 2000)
    await expect(barra).toBeInViewport()
  })

  test('pesquisa mesmo com a carga inicial ainda em andamento', async ({ page }) => {
    const cenario = 'busca durante a carga'

    printStep(cenario, 'Configurando mock: carga inicial que não termina')
    await page.route('**/api/documentos/**', async (route) => {
      if (new URL(route.request().url()).searchParams.has('termo')) {
        await route.fulfill({ json: EMPTY_RESPONSE })
      }
      // Sem termo (carga inicial): a requisição fica pendente.
    })
    await page.route('**/api/dashboard*', (route) =>
      route.fulfill({ status: 500 }),
    )

    printStep(cenario, 'Pesquisando antes da carga inicial terminar')
    await abrirFrontend(page)
    await expect(page.locator('.results-count')).toHaveText('Buscando...')
    await page.getByLabel('Descrição do caso').fill('dano moral')
    await page.getByLabel('Descrição do caso').press('Enter')

    printStep(cenario, 'Validando que a pesquisa foi feita')
    const barra = page.getByRole('region', { name: 'Busca e filtros' })
    await expect(barra.getByLabel('Descrição do caso')).toHaveValue('dano moral')
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
    await expect(page.locator('.results-count')).not.toHaveText('Buscando...')
    const totalAposCarregamentoInicial = requisicoes.length

    printStep(cenario, 'Enviando a busca sem descrição')
    await page.getByRole('button', { name: /pesquisar/i }).click()

    printStep(cenario, 'Validando a mensagem de erro')
    await expect(page.getByRole('alert')).toHaveText(
      'Informe a descrição do caso para realizar a pesquisa.',
    )

    printStep(cenario, 'Validando que nenhuma busca foi iniciada')
    await page.waitForTimeout(200)
    expect(requisicoes).toHaveLength(totalAposCarregamentoInicial)
  })
})
