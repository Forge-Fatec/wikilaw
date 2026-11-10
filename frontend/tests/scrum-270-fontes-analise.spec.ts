import { test, expect, type Page } from '@playwright/test'
import { abrirFrontend, pesquisar } from './helpers/test-helper'

const DOCUMENTOS = Array.from({ length: 4 }, (_, index) => ({
  id: index + 1, fonte: 'STJ', categoria: 'decisoes',
  titulo: `Acórdão ${index + 1}`, resumoOuEmenta: 'Negativação indevida gera dano moral.', decisao: 'Recurso provido.',
  tribunal: 'STJ', dataPublicacao: `2025-01-0${index + 1}`,
}))

const URL_ORIGINAL = 'https://processo.stj.jus.br/processo/4'

const ANALISE = {
  resumo: 'O dano moral é presumido [1, 2] e o valor segue a razoabilidade.',
  questaoJuridica: 'Dano moral por negativação indevida.',
  entendimentoPredominante: 'Prevalece o dano presumido [1].',
  pontosPrincipais: ['Dispensa prova do prejuízo [2].', 'Ponto sem fonte verificável.'],
  fundamentosLegais: ['CDC, art. 14'],
  divergencias: [],
  documentosRelevantes: [{ numero: 1, categoria: 'decisoes', id: 4, titulo: 'Acórdão 4', motivo: 'Fixa a tese [1].' }],
  limitacoes: [],
  geradaPorIa: true,
  aviso: 'Análise gerada com auxílio de inteligência artificial.',
  modelo: 'gemini-3.5-flash',
  geradaEm: '2026-11-10T10:00:00-03:00',
  documentosAnalisados: 2,
  resultadosUtilizados: [{ posicao: 1, categoria: 'decisoes', id: 4 }, { posicao: 2, categoria: 'decisoes', id: 3 }],
  resultadosOmitidos: [],
  contextoResumido: false,
  fontes: [
    {
      numero: 1, categoria: 'decisoes', id: 4, titulo: 'Acórdão 4', fonte: 'STJ', tribunal: 'STJ',
      numeroProcessoOuTema: 'REsp 1.234.567/SP', identificadorExterno: 'STJ-4', url: URL_ORIGINAL,
      secoes: ['Resumo', 'Entendimento predominante', 'Documentos mais relevantes'],
    },
    {
      numero: 2, categoria: 'decisoes', id: 3, titulo: 'Acórdão 3', fonte: 'STJ', tribunal: 'STJ',
      numeroProcessoOuTema: 'REsp 7.654.321/RJ', identificadorExterno: null, url: null,
      secoes: ['Resumo', 'Pontos principais'],
    },
  ],
}

async function gerarAnalise(page: Page, analise: object = ANALISE) {
  await page.route('**/api/documentos/**', (route) => {
    const itens = new URL(route.request().url()).pathname.endsWith('/decisoes') ? DOCUMENTOS : []
    return route.fulfill({ json: { itens, pagina: 0, tamanho: 100, total: itens.length, totalPaginas: 1 } })
  })
  await page.route('**/api/pesquisas/analises', (route) => route.fulfill({ json: analise }))
  await abrirFrontend(page)
  await pesquisar(page, 'negativação indevida')
  await painel(page).getByRole('button', { name: 'Analisar com IA' }).click()
  await expect(painel(page).getByText('Fontes utilizadas')).toBeVisible()
}

const painel = (page: Page) => page.getByRole('region', { name: 'Análise jurídica com IA' })
const fontes = (page: Page) => painel(page).getByRole('region', { name: 'Fontes utilizadas' })

test('CA01: apresenta as fontes que fundamentam a análise, com a identificação na fonte original', async ({ page }) => {
  await gerarAnalise(page)

  const itens = fontes(page).getByRole('listitem')
  await expect(itens).toHaveCount(2)
  await expect(itens.nth(0)).toContainText('[1]')
  await expect(itens.nth(0)).toContainText('Acórdão 4')
  await expect(itens.nth(0)).toContainText('Decisão judicial · STJ · REsp 1.234.567/SP · ID na fonte: STJ-4')
  await expect(itens.nth(1)).toContainText('REsp 7.654.321/RJ')

  await fontes(page).getByRole('button', { name: 'Acórdão 3' }).click()
  await expect(page.getByRole('dialog')).toContainText('Acórdão 3')
})

test('CA02: leva ao documento jurídico original quando a fonte tem endereço de acesso', async ({ page }) => {
  await page.context().route(`${URL_ORIGINAL}*`, (route) => route.fulfill({ body: '<h1>Processo 4</h1>', contentType: 'text/html' }))
  await gerarAnalise(page)

  const link = fontes(page).getByRole('link', { name: /Abrir original/ })
  await expect(link).toHaveCount(1)
  await expect(link).toHaveAttribute('href', URL_ORIGINAL)
  await expect(link).toHaveAttribute('target', '_blank')
  await expect(link).toHaveAttribute('rel', /noopener/)

  const [original] = await Promise.all([page.waitForEvent('popup'), link.click()])
  await expect(original).toHaveURL(URL_ORIGINAL)

  await expect(fontes(page).getByRole('listitem').nth(1)).toContainText('Original indisponível')
})

test('CA03: cada citação leva à fonte que sustenta a informação', async ({ page }) => {
  await gerarAnalise(page)

  await expect(fontes(page).getByRole('listitem').nth(0)).toContainText('Sustenta: Resumo, Entendimento predominante, Documentos mais relevantes')
  await expect(fontes(page).getByRole('listitem').nth(1)).toContainText('Sustenta: Resumo, Pontos principais')

  const resumo = painel(page).locator('.ai-summary')
  await expect(resumo).toContainText('O dano moral é presumido [1, 2]')
  await resumo.getByRole('link', { name: 'Fonte 2' }).click()
  await expect(page.locator('#fonte-analise-2')).toBeFocused()
})

test('CA04: não atribui referência a informações sem fonte verificável', async ({ page }) => {
  await gerarAnalise(page, {
    ...ANALISE,
    resumo: 'Tese citada sem fonte disponível [7].',
    pontosPrincipais: ['Ponto sem fonte verificável.'],
    documentosRelevantes: [],
    fontes: [],
  })

  await expect(fontes(page)).toContainText('Nenhuma informação desta análise foi atribuída a um documento verificável')
  await expect(painel(page).locator('.ai-summary').getByRole('link')).toHaveCount(0)
  await expect(fontes(page).getByRole('link')).toHaveCount(0)
})
