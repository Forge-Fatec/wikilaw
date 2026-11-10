import { test, expect, type Page } from '@playwright/test'
import { abrirFrontend, pesquisar } from './helpers/test-helper'

const DOCUMENTOS = Array.from({ length: 12 }, (_, index) => ({
  id: index + 1, fonte: 'STJ', categoria: 'decisoes',
  titulo: `Acórdão ${index + 1}`, resumoOuEmenta: 'Negativação indevida gera dano moral.', decisao: 'Recurso provido.',
  tribunal: 'STJ', dataPublicacao: `2025-01-${String(index + 1).padStart(2, '0')}`,
}))

const ANALISE = {
  resumo: 'Os acórdãos reconhecem o dano moral presumido [1, 3].',
  questaoJuridica: 'Dano moral por negativação indevida.',
  entendimentoPredominante: 'Dano presumido.',
  pontosPrincipais: [],
  fundamentosLegais: [],
  divergencias: [],
  documentosRelevantes: [],
  limitacoes: [],
  geradaPorIa: true,
  aviso: 'Análise gerada com auxílio de inteligência artificial.',
  modelo: 'gemini-3.5-flash',
  geradaEm: '2026-11-09T10:00:00-03:00',
  documentosAnalisados: 2,
  resultadosUtilizados: [{ posicao: 1, categoria: 'decisoes', id: 12 }, { posicao: 3, categoria: 'decisoes', id: 10 }],
  resultadosOmitidos: Array.from({ length: 10 }, (_, index) => ({ categoria: 'decisoes', id: index + 1 })),
  contextoResumido: true,
}

interface Payload {
  termo: string | null
  pesquisa: {
    modo: string
    categorias: string[]
    fontes: string[]
    tribunal: string | null
    dataDe: string | null
    dataAte: string | null
    ordenacao: string
    resultadoDecisao: string | null
    orgaoJulgador: string | null
    totalResultados: number
  }
  resultados: { categoria: string, id: number }[]
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

test('CA01: envia a pesquisa ativa e todos os seus resultados, na ordem exibida', async ({ page }) => {
  let payload: Payload | undefined
  await page.route('**/api/pesquisas/analises', (route) => {
    payload = route.request().postDataJSON()
    return route.fulfill({ json: ANALISE })
  })
  await preparar(page)

  await expect(painel(page)).toContainText('Usa a pesquisa atual (12 resultados')
  await painel(page).getByRole('button', { name: 'Analisar com IA' }).click()
  await expect(painel(page).getByText(ANALISE.resumo)).toBeVisible()

  expect(payload?.termo).toBe('negativação indevida')
  expect(payload?.resultados).toHaveLength(12)
  expect(payload?.resultados[0]).toEqual({ categoria: 'decisoes', id: 12 })
  expect(payload?.pesquisa).toMatchObject({
    modo: 'textual',
    fontes: [],
    tribunal: null,
    dataDe: null,
    dataAte: null,
    ordenacao: 'mais-recentes',
    resultadoDecisao: null,
    orgaoJulgador: null,
    totalResultados: 12,
  })
  expect(payload?.pesquisa.categorias).toContain('decisoes')
})

test('CA04: mostra quais resultados da pesquisa foram usados como contexto', async ({ page }) => {
  await page.route('**/api/pesquisas/analises', (route) => route.fulfill({ json: ANALISE }))
  await preparar(page)

  await painel(page).getByRole('button', { name: 'Analisar com IA' }).click()

  await expect(painel(page)).toContainText('Baseada em 2 dos 12 resultados da pesquisa atual.')
  await expect(painel(page)).toContainText('Resultados usados como contexto: posições 1, 3. 10 resultados ficaram de fora pelo limite da IA.')
})

test('acompanha a ordenação da pesquisa ativa e descarta a análise anterior', async ({ page }) => {
  const payloads: Payload[] = []
  await page.route('**/api/pesquisas/analises', (route) => {
    payloads.push(route.request().postDataJSON())
    return route.fulfill({ json: ANALISE })
  })
  await preparar(page)
  await painel(page).getByRole('button', { name: 'Analisar com IA' }).click()
  await expect(painel(page).getByText(ANALISE.resumo)).toBeVisible()

  await page.getByRole('button', { name: 'Mais antigos' }).click()

  await expect(painel(page).getByText(ANALISE.resumo)).toHaveCount(0)
  await painel(page).getByRole('button', { name: 'Analisar com IA' }).click()
  await expect(painel(page).getByText(ANALISE.resumo)).toBeVisible()
  expect(payloads).toHaveLength(2)
  expect(payloads[1].pesquisa.ordenacao).toBe('mais-antigos')
  expect(payloads[1].resultados[0]).toEqual({ categoria: 'decisoes', id: 1 })
})

test('CA03: informa quando não há resultados para contextualizar a análise', async ({ page }) => {
  await page.route('**/api/pesquisas/analises', (route) => route.fulfill({
    status: 422,
    json: { status: 422, error: 'Não existem resultados suficientes para contextualizar a análise.', details: [] },
  }))
  await preparar(page)

  await painel(page).getByRole('button', { name: 'Analisar com IA' }).click()

  await expect(painel(page).getByRole('alert'))
    .toContainText('Não existem resultados suficientes para contextualizar a análise.')
})
