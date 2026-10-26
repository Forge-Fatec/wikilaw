import { API_BASE } from './api'
import { apiFetch } from './auth'

export interface CaseDocument {
  nome: string
  formato: 'PDF' | 'DOCX'
  tamanhoBytes: number
  caracteres: number
  paginas: number | null
  texto: string
}

export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024

export async function apiError(response: Response, fallback: string): Promise<Error> {
  try {
    const data = await response.json()
    if (typeof data.error === 'string' && data.error.length <= 600) return new Error(data.error)
  } catch { /* The server or proxy may return HTML instead of the JSON error contract. */ }
  return new Error(response.status === 413 ? 'O arquivo excede o limite de 10 MB.' : fallback)
}

export async function uploadCaseDocument(file: File, signal: AbortSignal): Promise<CaseDocument> {
  const form = new FormData()
  form.append('arquivo', file)
  const response = await apiFetch(`${API_BASE}/api/pesquisas/documentos`, { method: 'POST', body: form, signal })
  if (!response.ok) throw await apiError(response, 'Não foi possível processar o documento. Tente novamente.')
  const data = await response.json()
  if (typeof data.texto !== 'string' || !data.texto.trim() || data.texto.length > 100000
      || typeof data.nome !== 'string' || !['PDF', 'DOCX'].includes(data.formato)) {
    throw new Error('A resposta do documento é inválida. Tente enviá-lo novamente.')
  }
  return data as CaseDocument
}

export async function prepareSearchContext(termo: string, texto: string, signal: AbortSignal): Promise<string> {
  const response = await apiFetch(`${API_BASE}/api/pesquisas/contexto`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, signal,
    body: JSON.stringify({ termo, texto }),
  })
  if (!response.ok) throw await apiError(response, 'Não foi possível usar o contexto do documento. Tente novamente.')
  const data = await response.json()
  if (typeof data.consulta !== 'string' || !data.consulta.trim() || data.consulta.length > 300) {
    throw new Error('Não foi possível preparar a pesquisa com o documento.')
  }
  return data.consulta
}
