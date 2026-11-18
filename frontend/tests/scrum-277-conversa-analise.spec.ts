import { test, expect, type Page } from '@playwright/test'
import { abrirFrontend, pesquisar } from './helpers/test-helper'

const DOCUMENTOS = Array.from({ length: 3 }, (_, index) => ({
  id: index + 1, fonte: 'STJ', categoria: 'decisoes',
  titulo: `Acórdão ${index + 1}`, resumoOuEmenta: 'Negativação indevida gera dano moral.', decisao: 'Recurso provido.',
  tribunal: 'STJ', dataPublicacao: `2025-01-0${index + 1}`,
}))

const ANALISE = {
  resumo: 'Os acórdãos reconhecem o dano moral presumido na negativação indevida [1, 2].',
  questaoJuridica: null, entendimentoPredominante: null,
  pontosPrincipais: [], fundamentosLegais: [], divergencias: [], documentosRelevantes: [], limitacoes: [],
  geradaPorIa: true, aviso: 'Análise gerada com auxílio de inteligência artificial.',
  modelo: 'gemini-3.5-flash', geradaEm: '2026-11-12T10:00:00-03:00', documentosAnalisados: 3,
}

function resposta(texto: string, posicao: number) {
  return {
    resposta: texto,
    documentosCitados: [{ posicao, categoria: 'decisoes', id: 4 - posicao, titulo: `Acórdão ${4 - posicao}` }],
    aviso: ANALISE.aviso, modelo: 'gemini-3.5-flash', geradaEm: '2026-11-12T10:01:00-03:00', historicoResumido: false,
  }
}

function printStep(cenario: string, etapa: string): void {
  console.info(`[conversa-analise][${cenario}] ${etapa}`)
}

async function gerarAnalise(page: Page) {
  await page.route('**/api/documentos/**', (route) => {
    const itens = new URL(route.request().url()).pathname.endsWith('/decisoes') ? DOCUMENTOS : []
    return route.fulfill({ json: { itens, pagina: 0, tamanho: 100, total: itens.length, totalPaginas: 1 } })
  })
  await page.route('**/api/pesquisas/analises', (route) => route.fulfill({ json: ANALISE }))
  await abrirFrontend(page)
  await pesquisar(page, 'negativação indevida')
  await painel(page).getByRole('button', { name: 'Analisar com IA' }).click()
  await expect(painel(page).getByText(ANALISE.resumo)).toBeVisible()
}

const painel = (page: Page) => page.getByRole('region', { name: 'Análise jurídica com IA' })
const conversa = (page: Page) => page.getByRole('region', { name: 'Perguntas sobre a análise' })

async function perguntar(page: Page, texto: string) {
  await conversa(page).getByLabel('Pergunta sobre a análise').fill(texto)
  await conversa(page).getByRole('button', { name: 'Perguntar' }).click()
}

test.describe('SCRUM-277 — interagir com a análise jurídica gerada', () => {
  test('mantém o contexto da conversa entre as perguntas', async ({ page }) => {
    const cenario = 'continuidade'
    const pedidos: { pergunta: string, historico: { pergunta: string, resposta: string }[], analise: { resultados: unknown[] } }[] = []
    await page.route('**/api/pesquisas/analises/perguntas', (route) => {
      const corpo = route.request().postDataJSON()
      pedidos.push(corpo)
      return route.fulfill({ json: pedidos.length === 1
        ? resposta('Sim, o dano é presumido [1].', 1)
        : resposta('No STJ o entendimento é o mesmo [2].', 2) })
    })

    printStep(cenario, 'Gerando a análise')
    await gerarAnalise(page)

    printStep(cenario, 'Primeira pergunta: vai sem histórico, com os resultados da análise')
    await perguntar(page, 'O dano moral é presumido?')
    await expect(conversa(page).getByText('Sim, o dano é presumido [1].')).toBeVisible()
    expect(pedidos[0].pergunta).toBe('O dano moral é presumido?')
    expect(pedidos[0].historico).toEqual([])
    expect(pedidos[0].analise.resultados).toHaveLength(3)

    printStep(cenario, 'Segunda pergunta: leva a troca anterior no histórico')
    await perguntar(page, 'E no STJ?')
    await expect(conversa(page).getByText('No STJ o entendimento é o mesmo [2].')).toBeVisible()
    expect(pedidos[1].historico).toEqual([
      { pergunta: 'O dano moral é presumido?', resposta: 'Sim, o dano é presumido [1].' },
    ])

    printStep(cenario, 'Histórico na tela e citação que abre o documento')
    await expect(conversa(page).getByRole('list', { name: 'Histórico de perguntas e respostas' }).locator('> li')).toHaveCount(2)
    await conversa(page).getByRole('button', { name: '[2] Acórdão 2' }).click()
    await expect(page.getByRole('dialog')).toContainText('Acórdão 2')
    await page.keyboard.press('Escape')

    printStep(cenario, 'Nova conversa limpa o histórico')
    await conversa(page).getByRole('button', { name: 'Nova conversa' }).click()
    await expect(conversa(page).getByRole('list', { name: 'Histórico de perguntas e respostas' })).toHaveCount(0)
  })

  test('mostra o erro da IA sem perder a conversa e permite perguntar de novo', async ({ page }) => {
    const cenario = 'tratamento de erros'
    let chamadas = 0
    await page.route('**/api/pesquisas/analises/perguntas', (route) => {
      chamadas += 1
      if (chamadas === 2) return route.fulfill({ status: 429, json: { error: 'Análise por IA indisponível' } })
      return route.fulfill({ json: resposta(chamadas === 1 ? 'Primeira resposta [1].' : 'Resposta depois do erro [1].', 1) })
    })

    printStep(cenario, 'Gerando a análise e fazendo a primeira pergunta')
    await gerarAnalise(page)
    await perguntar(page, 'Primeira pergunta')
    await expect(conversa(page).getByText('Primeira resposta [1].')).toBeVisible()

    printStep(cenario, 'Limite da IA: mensagem de erro, conversa e pergunta mantidas')
    await perguntar(page, 'Segunda pergunta')
    await expect(conversa(page).getByRole('alert')).toContainText('O limite de uso da IA foi atingido')
    await expect(conversa(page).getByText('Primeira resposta [1].')).toBeVisible()
    await expect(conversa(page).getByLabel('Pergunta sobre a análise')).toHaveValue('Segunda pergunta')

    printStep(cenario, 'Perguntando de novo: a resposta chega e o erro some')
    await conversa(page).getByRole('button', { name: 'Perguntar' }).click()
    await expect(conversa(page).getByText('Resposta depois do erro [1].')).toBeVisible()
    await expect(conversa(page).getByRole('alert')).toHaveCount(0)
  })
})
