import { expect, test, type Page } from '@playwright/test'
import { abrirEPesquisar, abrirFiltros } from './helpers/test-helper'

const PESQUISA = {
  indicadores: { totalDecisoes: 3, classificadas: 3, favoraveis: 2, aderencia: 67 },
  distribuicaoMerito: [
    { categoria: 'Provido', quantidade: 2 },
    { categoria: 'Desprovido', quantidade: 1 },
  ],
  distribuicaoCamaraTurma: [{ categoria: '1ª Turma Cível', quantidade: 3 }],
  distribuicaoTribunal: [{ categoria: 'TJSP', quantidade: 3 }],
  evolucaoAnual: [{ categoria: '2025', quantidade: 3 }],
}

const TRIBUNAL = {
  indicadores: { totalDecisoes: 18, classificadas: 15, favoraveis: 6, aderencia: 40 },
  distribuicaoMerito: [
    { categoria: 'Provido', quantidade: 6 },
    { categoria: 'Desprovido', quantidade: 9 },
    { categoria: 'Não identificado', quantidade: 3 },
  ],
  distribuicaoCamaraTurma: [{ categoria: '2ª Turma Cível', quantidade: 18 }],
  distribuicaoTribunal: [{ categoria: 'TJSP', quantidade: 18 }],
  evolucaoAnual: [{ categoria: '2024', quantidade: 18 }],
}

async function prepararPesquisa(page: Page): Promise<URLSearchParams[]> {
  const consultas: URLSearchParams[] = []
  await page.route('**/api/documentos/**', async (route) => {
    const itens = route.request().url().includes('/decisoes')
      ? [{
          id: 1,
          fonte: 'TJDFT',
          categoria: 'decisoes',
          titulo: 'Decisão sobre dano moral',
          tribunal: 'TJSP',
          orgaoJulgador: '1ª Turma Cível',
          dataPublicacao: '2025-01-10',
        }]
      : []
    await route.fulfill({ json: {
      itens, pagina: 0, tamanho: 100, total: itens.length, totalPaginas: itens.length ? 1 : 0,
    } })
  })
  await page.route('**/api/dashboard?*', async (route) => {
    const params = new URL(route.request().url()).searchParams
    consultas.push(params)
    await route.fulfill({ json: params.has('termo') ? PESQUISA : TRIBUNAL })
  })

  await abrirEPesquisar(page, 'dano moral')
  await abrirFiltros(page)
  await page.getByLabel('TRIBUNAL').selectOption('TJSP')
  await expect(indicadores(page, 'Indicadores da pesquisa').locator('.indicador-valor'))
    .toHaveText(['67%', '3'])
  await expect.poll(() => consultas.at(-1)?.toString())
    .toBe('termo=dano+moral&tribunal=TJSP')
  return consultas
}

function painel(page: Page) {
  return page.getByRole('complementary', { name: 'Panorama da pesquisa' })
}

function indicadores(page: Page, nome: string) {
  return painel(page).getByRole('region', { name: nome })
}

test.describe('Alternância entre pesquisa e panorama do tribunal', () => {
  test('CA01 — exibe os dados gerais do tribunal para comparação', async ({ page }) => {
    const consultas = await prepararPesquisa(page)

    await painel(page).getByRole('button', { name: 'Panorama do tribunal' }).click()

    const panorama = painel(page).getByRole('region', { name: 'Panorama geral do tribunal' })
    await expect(panorama.getByRole('heading', { name: 'TJSP' })).toBeVisible()
    await expect(indicadores(page, 'Indicadores do tribunal').locator('.indicador-valor'))
      .toHaveText(['40%', '18'])
    await expect(panorama).toContainText('6 de 15 decisões favoráveis ao recurso')
    await expect(panorama.getByRole('heading', { name: 'Decisões do tribunal por resultado' }))
      .toBeVisible()
    await expect(panorama).toContainText('Desprovido')
    await expect.poll(() => consultas.at(-1)?.toString()).toBe('tribunal=TJSP')
  })

  test('CA02 — retorna aos indicadores da pesquisa ativa', async ({ page }) => {
    await prepararPesquisa(page)
    await painel(page).getByRole('button', { name: 'Panorama do tribunal' }).click()
    await expect(indicadores(page, 'Indicadores do tribunal').locator('.indicador-valor'))
      .toHaveText(['40%', '18'])

    await painel(page).getByRole('button', { name: 'Pesquisa', exact: true }).click()

    await expect(indicadores(page, 'Indicadores da pesquisa').locator('.indicador-valor'))
      .toHaveText(['67%', '3'])
    await expect(indicadores(page, 'Indicadores da pesquisa'))
      .toContainText('2 de 3 decisões favoráveis ao recurso')
    await expect(painel(page).getByRole('heading', { name: 'Resultado dos recursos' }))
      .toBeVisible()
    await expect(indicadores(page, 'Indicadores do tribunal')).toHaveCount(0)
  })

  test('CA03 — identifica o escopo apresentado após cada alternância', async ({ page }) => {
    await prepararPesquisa(page)
    const pesquisa = painel(page).getByRole('button', { name: 'Pesquisa', exact: true })
    const panorama = painel(page).getByRole('button', { name: 'Panorama do tribunal' })
    const escopo = painel(page).locator('.escopo-atual')

    await expect(pesquisa).toHaveAttribute('aria-pressed', 'true')
    await expect(panorama).toHaveAttribute('aria-pressed', 'false')
    await expect(escopo).toContainText('Pesquisa: “dano moral”')
    await expect(indicadores(page, 'Indicadores da pesquisa')).toBeVisible()

    await panorama.click()
    await expect(pesquisa).toHaveAttribute('aria-pressed', 'false')
    await expect(panorama).toHaveAttribute('aria-pressed', 'true')
    await expect(escopo).toContainText('Panorama geral do TJSP')
    await expect(indicadores(page, 'Indicadores do tribunal')).toBeVisible()
    await expect(indicadores(page, 'Indicadores da pesquisa')).toHaveCount(0)

    await pesquisa.click()
    await expect(pesquisa).toHaveAttribute('aria-pressed', 'true')
    await expect(panorama).toHaveAttribute('aria-pressed', 'false')
    await expect(escopo).toContainText('Pesquisa: “dano moral”')
    await expect(indicadores(page, 'Indicadores da pesquisa')).toBeVisible()
    await expect(indicadores(page, 'Indicadores do tribunal')).toHaveCount(0)
  })
})
