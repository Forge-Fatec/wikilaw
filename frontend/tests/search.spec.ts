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
    await expect(page.getByRole('button', { name: /pesquisar/i })).toBeEnabled()
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
    await expect(page.getByRole('button', { name: /pesquisar/i })).toBeEnabled()
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
