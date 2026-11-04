import { API_BASE } from './api'
import { apiFetch } from './auth'

/** Mesmo limite do back (AnaliseJuridicaRequest.MAX_DOCUMENTOS). */
export const MAX_DOCUMENTOS_ANALISE = 10

export interface ReferenciaDocumento {
  categoria: string
  id: number
}

export interface DocumentoRelevante extends ReferenciaDocumento {
  titulo: string | null
  motivo: string | null
}

export interface AnaliseJuridica {
  resumo: string
  questaoJuridica: string | null
  entendimentoPredominante: string | null
  pontosPrincipais: string[]
  fundamentosLegais: string[]
  divergencias: string[]
  documentosRelevantes: DocumentoRelevante[]
  limitacoes: string[]
  geradaPorIa: boolean
  aviso: string
  modelo: string
  geradaEm: string
  documentosAnalisados: number
}

export async function gerarAnaliseJuridica(
  termo: string,
  resultados: ReferenciaDocumento[],
  signal?: AbortSignal,
): Promise<AnaliseJuridica> {
  let response: Response
  try {
    response = await apiFetch(`${API_BASE}/api/pesquisas/analises`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        termo: termo.slice(0, 2000) || null,
        resultados: resultados.slice(0, MAX_DOCUMENTOS_ANALISE),
      }),
      signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new Error('Não foi possível falar com o servidor. Verifique a conexão e tente novamente.', { cause: error })
  }
  if (!response.ok) {
    if (response.status === 429) throw new Error('O limite de uso da IA foi atingido. Aguarde alguns instantes e tente novamente.')
    if (response.status === 503) throw new Error('O serviço de IA está indisponível no momento. Tente novamente em instantes.')
    if (response.status === 409) throw new Error('Um resultado não está mais disponível. Refaça a pesquisa e tente novamente.')
    if (response.status === 400) throw new Error('Não foi possível analisar estes resultados. Refaça a pesquisa e tente novamente.')
    throw new Error('Não foi possível gerar a análise. Tente novamente.')
  }
  return response.json()
}
