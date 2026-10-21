import { API_BASE } from './api'
import { apiError } from './caseDocument'

export interface SimilaridadeStatus { habilitada: boolean; indexados: number; pendentes: number }
export interface SimilaridadeResults<T> {
  itens: { documento: T; similaridade: number; trecho: string }[]
  indexados: number
  pendentes: number
  limite: number
}

export async function buscarSimilares<T>(termo: string, texto: string | undefined, signal: AbortSignal): Promise<SimilaridadeResults<T>> {
  const response = await fetch(`${API_BASE}/api/pesquisas/similaridade`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal,
    body: JSON.stringify({ termo, texto, limite: 200 }),
  })
  if (!response.ok) throw await apiError(response, 'Não foi possível realizar a busca por similaridade. Tente novamente ou escolha Palavras-chave.')
  const data = await response.json()
  if (!Array.isArray(data.itens) || data.itens.length > 200) throw new Error('A resposta da pesquisa é inválida.')
  return data as SimilaridadeResults<T>
}
