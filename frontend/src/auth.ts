import { API_BASE } from './api'

export type UserRole = 'ADMIN' | 'USER'

export interface AuthUser {
  id: number
  login: string
  perfil: UserRole
  ativo: boolean
  criadoEm: string
}

let csrfToken: string | null = null

async function loadCsrfToken(): Promise<string> {
  const response = await fetch(`${API_BASE}/api/auth/csrf`, {
    credentials: 'include',
  })
  if (!response.ok) throw new Error('Não foi possível iniciar uma sessão segura.')
  const body = (await response.json()) as { token: string }
  csrfToken = body.token
  return body.token
}

export async function apiFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    headers.set('X-XSRF-TOKEN', csrfToken ?? (await loadCsrfToken()))
  }

  const response = await fetch(input, {
    ...init,
    headers,
    credentials: 'include',
  })
  if (response.status === 401) {
    window.dispatchEvent(new Event('wikilaw:unauthorized'))
  }
  return response
}

export async function responseError(
  response: Response,
  fallback: string,
): Promise<string> {
  try {
    const body = (await response.json()) as {
      detail?: string
      error?: string
      message?: string
      details?: string[]
    }
    return body.detail ?? body.error ?? body.message ?? body.details?.[0] ?? fallback
  } catch {
    return fallback
  }
}

export async function currentUser(): Promise<AuthUser | null> {
  const response = await fetch(`${API_BASE}/api/auth/me`, {
    credentials: 'include',
  })
  if (response.status === 401) return null
  if (!response.ok) throw new Error('Não foi possível verificar a sessão.')
  return response.json()
}

export async function signIn(login: string, senha: string): Promise<AuthUser> {
  const response = await apiFetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ login, senha }),
  })
  if (!response.ok) {
    throw new Error(await responseError(response, 'Login ou senha inválidos.'))
  }
  return response.json()
}

export async function signOut(): Promise<void> {
  const response = await apiFetch(`${API_BASE}/api/auth/logout`, { method: 'POST' })
  csrfToken = null
  if (!response.ok) throw new Error('Não foi possível encerrar a sessão.')
}
