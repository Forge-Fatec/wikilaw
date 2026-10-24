import { API_BASE } from './api'
import { apiFetch } from './auth'
import { apiError } from './caseDocument'

export interface SimilaridadeStatus { habilitada: boolean; indexados: number; pendentes: number }
export interface SimilaridadeResults<T> {
  itens: {
    documento: T
    similaridade: number
    trecho: string
    // Similaridade calibrada de 0 a 100, a seção do trecho e o porquê do resultado.
    percentual: number
    secao: string
    justificativa: string
  }[]
  indexados: number
  pendentes: number
  limite: number
}

interface ItemSemantico<T> {
  documento: T
  similaridade: number
  percentual: number
  trechoRelevante: { secao: string; texto: string }
  justificativa: string
}

// Mesmos limites do back na busca semântica (SemanticSearchRequest).
const LIMITE_RESULTADOS = 50
const MAXIMO_CARACTERES_POR_CAMPO = 20_000

// Usa a busca semântica (POST /api/pesquisas/semantica): a descrição digitada e o
// texto do documento anexado entram como partes do mesmo caso.
export async function buscarSimilares<T>(termo: string, texto: string | undefined, signal: AbortSignal): Promise<SimilaridadeResults<T>> {
  const fatos = texto?.trim().slice(0, MAXIMO_CARACTERES_POR_CAMPO)
  const response = await apiFetch(`${API_BASE}/api/pesquisas/semantica`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal,
    body: JSON.stringify({ descricao: termo || null, fatos: fatos || null, limite: LIMITE_RESULTADOS }),
  })
  if (response.status === 400) {
    throw new Error('Descreva o caso com pelo menos 20 caracteres ou anexe um documento para pesquisar por similaridade.')
  }
  if (response.status === 429) {
    throw new Error('A busca por similaridade recebeu muitas pesquisas agora. Tente de novo em alguns segundos.')
  }
  if (!response.ok) throw await apiError(response, 'Não foi possível realizar a busca por similaridade. Tente novamente ou escolha Palavras-chave.')
  const data = await response.json()
  if (!Array.isArray(data.resultados) || data.resultados.length > LIMITE_RESULTADOS) throw new Error('A resposta da pesquisa é inválida.')
  const itens = (data.resultados as ItemSemantico<T>[]).map((item) => ({
    documento: item.documento,
    similaridade: item.similaridade,
    trecho: item.trechoRelevante.texto,
    percentual: item.percentual,
    secao: item.trechoRelevante.secao,
    justificativa: item.justificativa,
  }))
  return { itens, indexados: itens.length, pendentes: 0, limite: LIMITE_RESULTADOS }
}
