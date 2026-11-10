import { expect, test, type Page } from '@playwright/test'
import { abrirFrontend, entrarComoAdmin, pesquisar } from './helpers/test-helper'

const CASO = 'O banco descontou da minha aposentadoria parcelas de um empréstimo que eu nunca contratei'
const MARCO_INICIAL = { decisoes: 120, precedentes: 45, doutrina: 7 }
const MARCO_DEPOIS = { decisoes: 131, precedentes: 45, doutrina: 7 }

function printStep(cenario: string, etapa: string): void {
  console.info(`[acompanhamentos][${cenario}] ${etapa}`)
}

function documento(id: number, categoria: string, titulo: string) {
  return { id, categoria, fonte: 'STJ', titulo, tribunal: 'TJSP', resumoOuEmenta: 'Ementa de teste.', dataPublicacao: '2026-01-01' }
}

function item(id: number, categoria: string, titulo: string, percentual: number) {
  return {
    documento: documento(id, categoria, titulo),
    similaridade: percentual / 100, percentual, trechosSemelhantes: 1,
    trechoRelevante: { secao: 'EMENTA', texto: `Trecho do documento ${id}.` },
    justificativa: 'A ementa deste documento se aproxima do caso descrito.',
  }
}

// Busca por similaridade ligada, um resultado na pesquisa e uma novidade (id 131) depois do marco.
async function mockarApi(page: Page) {
  const marcos = [MARCO_INICIAL, MARCO_DEPOIS]
  const consultas: unknown[] = []
  await page.route('**/api/pesquisas/similaridade/status', route => route.fulfill({
    json: { habilitada: true, indexados: 2, pendentes: 0 },
  }))
  await page.route('**/api/dashboard/resultados', route => route.fulfill({ json: {
    indicadores: { totalDecisoes: 0, classificadas: 0, favoraveis: 0, aderencia: null },
    distribuicaoMerito: [], distribuicaoCamaraTurma: [], distribuicaoTribunal: [], evolucaoAnual: [],
  } }))
  await page.route('**/api/pesquisas/semantica', route => route.fulfill({ json: {
    modelo: 'gemini-embedding-001',
    resultados: [item(1, 'precedentes', 'IRDR — empréstimo consignado', 78)],
  } }))
  await page.route('**/api/acompanhamentos/marco', route => route.fulfill({ json: marcos.shift() ?? MARCO_DEPOIS }))
  await page.route('**/api/acompanhamentos/novidades', route => {
    const corpo = route.request().postDataJSON() as { marco: typeof MARCO_INICIAL }
    consultas.push(corpo)
    const novidades = corpo.marco.decisoes < 131 ? [item(131, 'decisoes', 'Apelação — devolução em dobro', 84)] : []
    return route.fulfill({ json: { modelo: 'gemini-embedding-001', resultados: novidades } })
  })
  return consultas
}

test.describe('SCRUM-284 — acompanhar atualizações da pesquisa', () => {
  test('acompanha a pesquisa, mostra as novidades e guarda no navegador', async ({ page }) => {
    const cenario = 'acompanhar e verificar'
    const consultas = await mockarApi(page)

    printStep(cenario, 'Pesquisando por similaridade')
    await abrirFrontend(page)
    await pesquisar(page, CASO)
    await expect(page.locator('.card h3')).toHaveText(['IRDR — empréstimo consignado'])

    printStep(cenario, 'Acompanhando a pesquisa: o botão vira "Acompanhando"')
    await page.getByRole('button', { name: 'Acompanhar esta pesquisa' }).click()
    await expect(page.getByRole('button', { name: 'Acompanhando ✓' })).toBeDisabled()

    printStep(cenario, 'Verificando: a novidade aparece com percentual e justificativa')
    await page.getByRole('button', { name: /^Acompanhamentos/ }).click()
    const dialogo = page.getByRole('dialog', { name: 'Pesquisas acompanhadas' })
    await dialogo.getByRole('button', { name: 'Verificar agora' }).click()
    await expect(dialogo.getByText('1 novo')).toBeVisible()
    await expect(dialogo.getByText('Apelação — devolução em dobro')).toBeVisible()
    await expect(dialogo.getByText('84% similar')).toBeVisible()
    expect(consultas.at(-1)).toMatchObject({ descricao: CASO, marco: MARCO_INICIAL })

    printStep(cenario, 'Recarregando: o acompanhamento continua salvo no navegador')
    await page.keyboard.press('Escape')
    await page.reload({ waitUntil: 'domcontentloaded' })
    await entrarComoAdmin(page)
    await expect(page.getByRole('button', { name: /^Acompanhamentos/ })).toContainText('1')

    printStep(cenario, 'Marcando como vistas: o marco avança e a novidade sai')
    await page.getByRole('button', { name: /^Acompanhamentos/ }).click()
    await dialogo.getByRole('button', { name: 'Marcar como vistas' }).click()
    await expect(dialogo.getByText('0 novos')).toBeVisible()
    await expect(dialogo.getByText('Apelação — devolução em dobro')).toHaveCount(0)

    printStep(cenario, 'Deixando de acompanhar: a lista fica vazia')
    await dialogo.getByRole('button', { name: 'Deixar de acompanhar' }).click()
    await expect(dialogo.getByText('Nenhuma pesquisa acompanhada.', { exact: false })).toBeVisible()
  })
})
