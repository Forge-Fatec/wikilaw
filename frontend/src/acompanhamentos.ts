import { API_BASE } from './api'
import { apiFetch } from './auth'

// Pesquisas acompanhadas ficam só no navegador (localStorage), separadas por login.
// O servidor não guarda nada: ele só informa o marco atual e o que é mais novo que ele.

export interface MarcoAcompanhamento {
  decisoes: number
  precedentes: number
  doutrina: number
}

export interface NovidadeAcompanhamento {
  categoria: string
  id: number
  titulo: string
  fonte: string
  tribunal: string | null
  percentual: number
  secao: string
  trecho: string
  justificativa: string
}

export interface PesquisaAcompanhada {
  id: string
  descricao: string
  // Texto do documento anexado na pesquisa, quando houve um.
  fatos: string | null
  documento: string | null
  categorias: string[]
  marco: MarcoAcompanhamento
  criadaEm: string
  verificadaEm: string | null
  novidades: NovidadeAcompanhamento[]
  erro: string | null
}

export type NovaPesquisaAcompanhada = Pick<PesquisaAcompanhada, 'descricao' | 'fatos' | 'documento' | 'categorias'>

interface ItemNovidade {
  documento: { id: number; categoria: string; titulo: string; fonte: string; tribunal: string | null }
  percentual: number
  trechoRelevante: { secao: string; texto: string }
  justificativa: string
}

// Mesmo limite por campo da busca semântica no back.
const MAXIMO_CARACTERES_POR_CAMPO = 20_000

function chave(login: string): string {
  return `wikilaw:acompanhamentos:${login}`
}

// Sem localStorage (navegação privada, armazenamento bloqueado) a lista só vale enquanto a tela estiver aberta.
export function carregarAcompanhamentos(login: string): PesquisaAcompanhada[] {
  try {
    const salvo = JSON.parse(localStorage.getItem(chave(login)) ?? '[]')
    return Array.isArray(salvo) ? salvo : []
  } catch {
    return []
  }
}

export function salvarAcompanhamentos(login: string, lista: PesquisaAcompanhada[]): void {
  try {
    localStorage.setItem(chave(login), JSON.stringify(lista))
  } catch {
    // Mantém a lista só na memória da tela.
  }
}

// Mesma pesquisa = mesma descrição, mesmo documento e mesmos tipos.
export function mesmaPesquisa(a: NovaPesquisaAcompanhada, b: NovaPesquisaAcompanhada): boolean {
  return a.descricao === b.descricao && a.fatos === b.fatos
    && [...a.categorias].sort().join() === [...b.categorias].sort().join()
}

async function erroDaResposta(response: Response): Promise<Error> {
  if (response.status === 429) return new Error('A IA recebeu muitas pesquisas agora. A verificação será refeita mais tarde.')
  if (response.status === 503) return new Error('A pesquisa com IA está indisponível no momento.')
  return new Error('Não foi possível verificar esta pesquisa agora.')
}

export async function buscarMarco(): Promise<MarcoAcompanhamento> {
  const response = await apiFetch(`${API_BASE}/api/acompanhamentos/marco`)
  if (!response.ok) throw new Error('Não foi possível começar a acompanhar a pesquisa. Tente novamente.')
  return response.json()
}

export async function buscarNovidades(pesquisa: PesquisaAcompanhada): Promise<NovidadeAcompanhamento[]> {
  const response = await apiFetch(`${API_BASE}/api/acompanhamentos/novidades`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      descricao: pesquisa.descricao || null,
      fatos: pesquisa.fatos?.slice(0, MAXIMO_CARACTERES_POR_CAMPO) || null,
      categorias: pesquisa.categorias,
      marco: pesquisa.marco,
    }),
  })
  if (!response.ok) throw await erroDaResposta(response)
  const corpo = (await response.json()) as { resultados: ItemNovidade[] }
  return corpo.resultados.map((item) => ({
    categoria: item.documento.categoria,
    id: item.documento.id,
    titulo: item.documento.titulo,
    fonte: item.documento.fonte,
    tribunal: item.documento.tribunal,
    percentual: item.percentual,
    secao: item.trechoRelevante.secao,
    trecho: item.trechoRelevante.texto,
    justificativa: item.justificativa,
  }))
}
