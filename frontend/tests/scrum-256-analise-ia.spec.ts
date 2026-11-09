import { test, expect, type Page } from '@playwright/test'
import { abrirFrontend, pesquisar } from './helpers/test-helper'

const DOCUMENTOS = Array.from({ length: 12 }, (_, index) => ({
  id: index + 1, fonte: 'STJ', categoria: 'decisoes',
  titulo: `Acórdão ${index + 1}`, resumoOuEmenta: 'Negativação indevida gera dano moral.', decisao: 'Recurso provido.',
  tribunal: 'STJ', dataPublicacao: `2025-01-${String(index + 1).padStart(2, '0')}`,
}))

const ANALISE = {
  resumo: 'Os acórdãos reconhecem o dano moral presumido na negativação indevida [1, 2].',
  questaoJuridica: 'Cabimento de dano moral por inscrição indevida em cadastro de inadimplentes.',
  entendimentoPredominante: 'O dano moral é presumido (in re ipsa).',
  pontosPrincipais: ['Dispensa prova do prejuízo [1].', 'Valor fixado conforme a razoabilidade [2].'],
  fundamentosLegais: ['CDC, art. 14', 'Súmula 385/STJ'],
  divergencias: [],
  documentosRelevantes: [{ categoria: 'decisoes', id: 2, titulo: 'Acórdão 2', motivo: 'Fixa a tese principal.' }],
  limitacoes: ['A análise considera apenas os 10 primeiros resultados.'],
  geradaPorIa: true,
  aviso: 'Análise gerada com auxílio de inteligência artificial a partir dos documentos selecionados.',
  modelo: 'gemini-3.5-flash',
  geradaEm: '2026-11-03T21:00:00-03:00',
  documentosAnalisados: 10,
}

async function preparar(page: Page) {
  await page.route('**/api/documentos/**', (route) => {
    const itens = new URL(route.request().url()).pathname.endsWith('/decisoes') ? DOCUMENTOS : []
    return route.fulfill({ json: { itens, pagina: 0, tamanho: 100, total: itens.length, totalPaginas: 1 } })
  })
  await abrirFrontend(page)
  await pesquisar(page, 'negativação indevida')
  await expect(page.getByText('12 documentos encontrados', { exact: false })).toBeVisible()
}

const painel = (page: Page) => page.getByRole('region', { name: 'Análise jurídica com IA' })

test('CA01, CA02 e CA04: gera a análise dos primeiros resultados e a identifica como conteúdo de IA', async ({ page }) => {
  let payload: { termo: string, resultados: { categoria: string, id: number }[] } | undefined
  await page.route('**/api/pesquisas/analises', (route) => {
    expect(route.request().method()).toBe('POST')
    payload = route.request().postDataJSON()
    return route.fulfill({ json: ANALISE })
  })
  await preparar(page)

  await painel(page).getByRole('button', { name: 'Analisar com IA' }).click()

  await expect(painel(page).getByText(ANALISE.resumo)).toBeVisible()
  expect(payload?.termo).toBe('negativação indevida')
  expect(payload?.resultados).toHaveLength(12)
  expect(payload?.resultados[0]).toEqual({ categoria: 'decisoes', id: 12 })

  // CA02: seções organizadas, com destaque para as informações principais.
  await expect(painel(page).getByText('Entendimento predominante')).toBeVisible()
  await expect(painel(page).getByText(ANALISE.entendimentoPredominante)).toBeVisible()
  await expect(painel(page).getByRole('listitem').filter({ hasText: 'Súmula 385/STJ' })).toBeVisible()
  await expect(painel(page).getByText('Dispensa prova do prejuízo [1].')).toBeVisible()

  // CA04: o conteúdo é identificado como gerado por IA.
  await expect(painel(page).getByRole('note')).toContainText('Conteúdo gerado por inteligência artificial.')
  await expect(painel(page)).toContainText('Gerada com auxílio de IA pelo modelo gemini-3.5-flash')

  await painel(page).getByRole('button', { name: 'Acórdão 2' }).click()
  await expect(page.getByRole('dialog')).toContainText('Acórdão 2')
})

test('CA03: informa a indisponibilidade da IA e permite tentar novamente', async ({ page }) => {
  let chamadas = 0
  await page.route('**/api/pesquisas/analises', (route) => {
    chamadas += 1
    if (chamadas === 1) {
      return route.fulfill({ status: 503, json: { status: 503, error: 'Análise por IA indisponível', details: [] } })
    }
    return route.fulfill({ json: ANALISE })
  })
  await preparar(page)

  await painel(page).getByRole('button', { name: 'Analisar com IA' }).click()
  await expect(painel(page).getByRole('alert')).toContainText('O serviço de IA está indisponível no momento.')

  await painel(page).getByRole('button', { name: 'Tentar novamente' }).click()
  await expect(painel(page).getByText(ANALISE.resumo)).toBeVisible()
  expect(chamadas).toBe(2)
})

test('descarta a análise quando uma nova pesquisa muda os resultados', async ({ page }) => {
  await page.route('**/api/pesquisas/analises', (route) => route.fulfill({ json: ANALISE }))
  await preparar(page)
  await painel(page).getByRole('button', { name: 'Analisar com IA' }).click()
  await expect(painel(page).getByText(ANALISE.resumo)).toBeVisible()

  await pesquisar(page, 'outro tema')

  await expect(painel(page).getByRole('button', { name: 'Analisar com IA' })).toBeVisible()
  await expect(painel(page).getByText(ANALISE.resumo)).toHaveCount(0)
})
