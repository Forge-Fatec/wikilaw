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

/** Âncora de cada fonte na lista de fontes, usada pelas citações [n] do texto da análise. */
export function idFonte(numero: number) {
  return `fonte-analise-${numero}`
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

/** Pergunta e resposta já trocadas na conversa sobre a análise; só existem na tela. */
export interface TrocaConversa {
  pergunta: string
  resposta: string
}

/** Documento citado na resposta; `posicao` é o número entre colchetes. */
export interface DocumentoCitado extends ReferenciaDocumento {
  posicao: number
  titulo: string | null
}

export interface RespostaPergunta {
  resposta: string
  documentosCitados: DocumentoCitado[]
  aviso: string
  modelo: string
  geradaEm: string
  historicoResumido: boolean
}

/** Mesmo limite de caracteres da pergunta no back (PerguntaAnaliseRequest). */
export const MAX_CARACTERES_PERGUNTA = 1000

/**
 * Pergunta complementar sobre a análise: vai junto a mesma pesquisa ativa (o back relê os documentos)
 * e as trocas anteriores, para a IA entender perguntas que dependem delas.
 */
export async function perguntarSobreAnalise(
  termo: string,
  pesquisa: PesquisaAtiva,
  resultados: ReferenciaDocumento[],
  pergunta: string,
  historico: TrocaConversa[],
  signal?: AbortSignal,
): Promise<RespostaPergunta> {
  let response: Response
  try {
    response = await apiFetch(`${API_BASE}/api/pesquisas/analises/perguntas`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        analise: {
          termo: termo.slice(0, 2000) || null,
          pesquisa: { ...pesquisa, consultaContextual: pesquisa.consultaContextual?.slice(0, 2000) || null },
          resultados: resultados.slice(0, MAX_RESULTADOS_ANALISE),
        },
        pergunta: pergunta.slice(0, MAX_CARACTERES_PERGUNTA),
        historico,
      }),
      signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new Error('Não foi possível falar com o servidor. Verifique a conexão e tente novamente.', { cause: error })
  }
  if (!response.ok) {
    if (response.status === 429) throw new Error('O limite de uso da IA foi atingido. Aguarde alguns instantes e pergunte de novo.')
    if (response.status === 503) throw new Error('O serviço de IA está indisponível no momento. Tente novamente em instantes.')
    if (response.status === 409) throw new Error('Um resultado não está mais disponível. Refaça a pesquisa e gere a análise de novo.')
    if (response.status === 400) throw new Error('Não foi possível enviar esta pergunta. Revise o texto e tente novamente.')
    throw new Error('Não foi possível responder à pergunta. Tente novamente.')
  }
  return response.json()
}
