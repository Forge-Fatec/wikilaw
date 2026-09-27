import { expect, test, type Page } from '@playwright/test'
import { abrirFrontend } from './helpers/test-helper'

const URL_FONTE = 'https://fonte-original.example.test/acordao/123'

function documento(id: number, titulo: string, urlOriginal: string | null) {
  return {
    id,
    fonte: 'TESTE',
    categoria: 'teste',
    titulo,
    tipoDocumento: null,
    numeroProcessoOuTema: null,
    autoresOuRelator: null,
    resumoOuEmenta: 'Ementa de teste.',
    decisao: null,
    tribunal: null,
    orgaoJulgador: null,
    dataJulgamento: null,
    dataPublicacao: '2026-01-15',
    dataOriginal: null,
    urlOriginal,
    idRegistroBruto: null,
  }
}

const ITENS_POR_CATEGORIA: Record<string, ReturnType<typeof documento>[]> = {
  decisoes: [documento(1, 'Acórdão com link', URL_FONTE)],
  precedentes: [documento(2, 'Precedente sem link', null)],
  doutrina: [documento(3, 'Doutrina com link inseguro', 'javascript:alert(1)')],
}

function printStep(cenario: string, etapa: string): void {
  console.info(`[fonte-original][${cenario}] ${etapa}`)
}

async function prepararPagina(page: Page): Promise<void> {
  await page.route('**/api/documentos/**', async (route) => {
    const categoria = new URL(route.request().url()).pathname.split('/').pop()
    const itens = ITENS_POR_CATEGORIA[categoria ?? ''] ?? []
    await route.fulfill({
      json: { itens, pagina: 0, tamanho: 50, total: itens.length, totalPaginas: 1 },
    })
  })
  // A nova aba abre em outro Page: o mock precisa valer para o contexto inteiro.
  await page.context().route(`${URL_FONTE}**`, (route) =>
    route.fulfill({ contentType: 'text/html', body: '<h1>Fonte original</h1>' }),
  )

  await abrirFrontend(page)
}

function card(page: Page, titulo: string) {
  return page.locator('article.card').filter({ hasText: titulo })
}

test.describe('acesso à fonte original de um resultado', () => {
  test('abre a fonte original em uma nova aba a partir do card', async ({
    page,
  }) => {
    const cenario = 'card com link'

    printStep(cenario, 'Abrindo o frontend com a API simulada')
    await prepararPagina(page)

    const link = card(page, 'Acórdão com link').getByRole('link', {
      name: /acessar fonte original/i,
    })
    await expect(link).toHaveAttribute('href', URL_FONTE)
    await expect(link).toHaveAttribute('target', '_blank')
    await expect(link).toHaveAttribute('rel', /noopener/)

    printStep(cenario, 'Clicando no link e aguardando a nova aba')
    const [novaAba] = await Promise.all([
      page.waitForEvent('popup'),
      link.click(),
    ])

    printStep(cenario, 'Validando a URL da nova aba')
    await expect(novaAba).toHaveURL(URL_FONTE)
    await expect(page).toHaveURL(/localhost/)
  })

  test('abre a fonte original em uma nova aba a partir dos detalhes', async ({
    page,
  }) => {
    const cenario = 'modal com link'

    printStep(cenario, 'Abrindo os detalhes do documento')
    await prepararPagina(page)
    await card(page, 'Acórdão com link')
      .getByRole('button', { name: /ver detalhes/i })
      .click()

    const link = page
      .getByRole('dialog')
      .getByRole('link', { name: /acessar fonte original/i })

    printStep(cenario, 'Clicando no link e aguardando a nova aba')
    const [novaAba] = await Promise.all([
      page.waitForEvent('popup'),
      link.click(),
    ])
    await expect(novaAba).toHaveURL(URL_FONTE)
  })

  test('desabilita o acesso quando o resultado não possui link', async ({
    page,
  }) => {
    const cenario = 'sem link'

    printStep(cenario, 'Validando o card sem URL')
    await prepararPagina(page)
    const cardSemLink = card(page, 'Precedente sem link')
    await expect(
      cardSemLink.getByRole('button', { name: /fonte indisponível/i }),
    ).toBeDisabled()
    await expect(cardSemLink.getByRole('link')).toHaveCount(0)

    printStep(cenario, 'Validando os detalhes sem URL')
    await cardSemLink.getByRole('button', { name: /ver detalhes/i }).click()
    const modal = page.getByRole('dialog')
    await expect(
      modal.getByRole('button', { name: /fonte indisponível/i }),
    ).toBeDisabled()
    await expect(modal.getByRole('link')).toHaveCount(0)
  })

  test('não oferece acesso a links que não sejam http(s)', async ({ page }) => {
    const cenario = 'link inseguro'

    printStep(cenario, 'Validando o card com URL javascript:')
    await prepararPagina(page)
    const cardInseguro = card(page, 'Doutrina com link inseguro')
    await expect(
      cardInseguro.getByRole('button', { name: /fonte indisponível/i }),
    ).toBeDisabled()
    await expect(cardInseguro.getByRole('link')).toHaveCount(0)
  })
})
