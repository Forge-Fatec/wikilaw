import { API_BASE } from './api'
import { apiFetch } from './auth'

/**
 * Quantos resultados da pesquisa ativa vão para o back (AnaliseJuridicaRequest.MAX_RESULTADOS).
 * O back escolhe entre eles os mais relevantes que cabem no limite de contexto da IA.
 */
export const MAX_RESULTADOS_ANALISE = 200

export interface ReferenciaDocumento {
  categoria: string
  id: number
}

/** Como a pesquisa exibida foi feita e os filtros aplicados nela (PesquisaAtivaRequest no back). */
export interface PesquisaAtiva {
  modo: 'textual' | 'similaridade'
  consultaContextual: string | null
  documentoContexto: string | null
  categorias: string[]
  fontes: string[]
  tribunal: string | null
  dataDe: string | null
  dataAte: string | null
  ordenacao: 'mais-recentes' | 'mais-antigos' | 'relevancia'
  resultadoDecisao: string | null
  orgaoJulgador: string | null
  totalResultados: number
}

export interface DocumentoRelevante extends ReferenciaDocumento {
  /** Posição na pesquisa, o mesmo número das citações [n]. */
  numero?: number
  titulo: string | null
  motivo: string | null
}

/** Documento que fundamenta a análise; o back só devolve fontes realmente citadas e verificadas. */
export interface FonteAnalise extends ReferenciaDocumento {
  /** Posição na pesquisa, o mesmo número das citações [n]. */
  numero: number
  titulo: string | null
  fonte: string | null
  tribunal: string | null
  numeroProcessoOuTema: string | null
  identificadorExterno: string | null
  /** Endereço do documento original (só http/https) ou null quando a fonte não informou. */
  url: string | null
  /** Seções da análise que citam esta fonte. */
  secoes: string[]
}

/** Resultado enviado à IA; `posicao` é a posição na lista e o número das citações, como [3]. */
export interface ResultadoUtilizado extends ReferenciaDocumento {
  posicao: number
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
  resultadosUtilizados?: ResultadoUtilizado[]
  resultadosOmitidos?: ReferenciaDocumento[]
  contextoResumido?: boolean
  fontes?: FonteAnalise[]
}

export async function gerarAnaliseJuridica(
  termo: string,
  pesquisa: PesquisaAtiva,
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
        pesquisa: { ...pesquisa, consultaContextual: pesquisa.consultaContextual?.slice(0, 2000) || null },
        resultados: resultados.slice(0, MAX_RESULTADOS_ANALISE),
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
    if (response.status === 422) throw new Error('Não existem resultados suficientes para contextualizar a análise. Faça uma pesquisa que retorne documentos e tente novamente.')
    if (response.status === 409) throw new Error('Um resultado não está mais disponível. Refaça a pesquisa e tente novamente.')
    if (response.status === 400) throw new Error('Não foi possível analisar estes resultados. Refaça a pesquisa e tente novamente.')
    throw new Error('Não foi possível gerar a análise. Tente novamente.')
  }
  return response.json()
}
