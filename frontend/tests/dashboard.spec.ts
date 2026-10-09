import { expect, test, type Page } from '@playwright/test'
import { classificarResultado } from '../src/indicadores'
import { abrirFrontend } from './helpers/test-helper'

const EMPTY_RESPONSE = {
  itens: [],
  pagina: 0,
  tamanho: 100,
  total: 0,
  totalPaginas: 0,
}

function printStep(cenario: string, etapa: string): void {
  console.info(`[dashboard][${cenario}] ${etapa}`)
}

function item(
  id: number,
  categoria: string,
  tribunal: string,
  decisao: string | null,
) {
  return {
    id,
    fonte: 'Fonte de teste',
    categoria,
    titulo: `Documento ${id}`,
    tipoDocumento: null,
    numeroProcessoOuTema: null,
    autoresOuRelator: null,
    resumoOuEmenta: null,
    decisao,
    tribunal,
    orgaoJulgador: null,
    dataJulgamento: null,
    dataPublicacao: `2024-01-${String(id).padStart(2, '0')}`,
    dataOriginal: null,
    urlOriginal: null,
    idRegistroBruto: null,
  }
}

const DECISOES = [
  item(1, 'decisoes', 'TJSP', 'Recurso provido.'),
  item(2, 'decisoes', 'TJSP', 'Recurso parcialmente provido.'),
  item(3, 'decisoes', 'TJRJ', 'Recurso desprovido.'),
  item(4, 'decisoes', 'TJRJ', 'Negaram provimento ao recurso.'),
  item(5, 'decisoes', 'TJSP', null),
  item(6, 'decisoes', 'TJSP', 'Homologado o acordo entre as partes.'),
]

// O campo "decisao" de precedentes traz a tese e não deve entrar na conta.
const PRECEDENTES = [
  item(7, 'precedentes', 'STJ', 'É procedente a restituição em dobro.'),
]

async function mockarApi(page: Page) {
  await page.route('**/api/documentos/**', async (route) => {
    const url = route.request().url()
    const itens = url.includes('/decisoes')
      ? DECISOES
      : url.includes('/precedentes')
        ? PRECEDENTES
        : []
    await route.fulfill({
      json: { ...EMPTY_RESPONSE, itens, total: itens.length, totalPaginas: 1 },
    })
  })
}

function indicador(page: Page, rotulo: string) {
  return page
    .getByRole('region', { name: 'Indicadores da pesquisa' })
    .locator('.indicador')
    .filter({ hasText: rotulo })
}

function valor(page: Page, rotulo: string) {
  return indicador(page, rotulo).locator('.indicador-valor')
}

test.describe('dashboard de indicadores da pesquisa', () => {
  test('classifica o resultado a partir do texto da decisão', () => {
    expect(classificarResultado('Recurso provido.')).toBe('provido')
    expect(classificarResultado('Deram provimento ao apelo.')).toBe('provido')
    expect(classificarResultado('Pedido julgado procedente.')).toBe('provido')
    expect(classificarResultado('Recurso parcialmente provido.')).toBe('parcial')
    expect(classificarResultado('Deram parcial provimento.')).toBe('parcial')
    expect(classificarResultado('Procedente em parte.')).toBe('parcial')
    expect(classificarResultado('Recurso desprovido.')).toBe('desprovido')
    expect(classificarResultado('Recurso improvido.')).toBe('desprovido')
    expect(classificarResultado('Recurso não provido.')).toBe('desprovido')
    expect(classificarResultado('Negou-se provimento.')).toBe('desprovido')
    expect(classificarResultado('Pedido improcedente.')).toBe('desprovido')
    expect(classificarResultado('Homologado o acordo.')).toBe('indefinido')
    expect(classificarResultado(null)).toBe('indefinido')
  })

  test('exibe aderência e total de decisões dos resultados', async ({ page }) => {
    const cenario = 'resultados carregados'

    printStep(cenario, 'Configurando mock da API')
    await mockarApi(page)

    printStep(cenario, 'Abrindo o frontend')
    await abrirFrontend(page)
    await expect(page.getByText('7 documentos encontrados')).toBeVisible()

    printStep(cenario, 'Validando os indicadores')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('50%')
    await expect(indicador(page, 'ADERÊNCIA')).toContainText(
      '2 de 4 decisões favoráveis ao recurso',
    )
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('6')
    await expect(indicador(page, 'DECISÕES ANALISADAS')).toContainText(
      '4 com resultado identificado',
    )
  })

  test('recalcula os indicadores ao aplicar filtros', async ({ page }) => {
    const cenario = 'filtro por tribunal'

    printStep(cenario, 'Configurando mock da API')
    await mockarApi(page)

    printStep(cenario, 'Abrindo o frontend')
    await abrirFrontend(page)
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('50%')

    printStep(cenario, 'Filtrando por TJRJ')
    await page.locator('.filters select').nth(1).selectOption('TJRJ')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('0%')
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('2')

    printStep(cenario, 'Filtrando por TJSP')
    await page.locator('.filters select').nth(1).selectOption('TJSP')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('100%')
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('4')
  })

  test('indica ausência de base de cálculo sem decisões', async ({ page }) => {
    const cenario = 'sem decisões'

    printStep(cenario, 'Configurando mock da API')
    await mockarApi(page)

    printStep(cenario, 'Abrindo o frontend')
    await abrirFrontend(page)

    printStep(cenario, 'Removendo jurisprudência dos filtros')
    await page.getByLabel('Jurisprudência').uncheck()

    printStep(cenario, 'Validando os indicadores vazios')
    await expect(valor(page, 'ADERÊNCIA')).toHaveText('—')
    await expect(indicador(page, 'ADERÊNCIA')).toContainText(
      'Nenhuma decisão com resultado identificado',
    )
    await expect(valor(page, 'DECISÕES ANALISADAS')).toHaveText('0')
  })
})
