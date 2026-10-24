import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { API_BASE } from '../api'
import { apiFetch, responseError, type AuthUser, type UserRole } from '../auth'

export function AdminUsersDialog({
  currentUser,
  onClose,
}: {
  currentUser: AuthUser
  onClose: () => void
}) {
  const [users, setUsers] = useState<AuthUser[]>([])
  const [login, setLogin] = useState('')
  const [senha, setSenha] = useState('')
  const [perfil, setPerfil] = useState<UserRole>('USER')
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [salvando, setSalvando] = useState(false)
  const closeButton = useRef<HTMLButtonElement>(null)

  const loadUsers = useCallback(async () => {
    const response = await apiFetch(`${API_BASE}/api/admin/usuarios`)
    if (!response.ok) throw new Error(await responseError(response, 'Não foi possível carregar os usuários.'))
    setUsers(await response.json())
  }, [])

  useEffect(() => {
    let active = true
    void apiFetch(`${API_BASE}/api/admin/usuarios`)
      .then(async (response) => {
        if (!response.ok) throw new Error(await responseError(response, 'Não foi possível carregar os usuários.'))
        return response.json() as Promise<AuthUser[]>
      })
      .then((data) => {
        if (active) setUsers(data)
      })
      .catch((error) => {
        if (active) setErro(error instanceof Error ? error.message : 'Erro inesperado.')
      })
      .finally(() => {
        if (active) setCarregando(false)
      })
    closeButton.current?.focus()
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    function escape(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', escape)
    return () => window.removeEventListener('keydown', escape)
  }, [onClose])

  async function createUser(event: FormEvent) {
    event.preventDefault()
    setErro(null)
    setSalvando(true)
    try {
      const response = await apiFetch(`${API_BASE}/api/admin/usuarios`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ login, senha, perfil }),
      })
      if (!response.ok) throw new Error(await responseError(response, 'Não foi possível criar o usuário.'))
      setLogin('')
      setSenha('')
      setPerfil('USER')
      await loadUsers()
    } catch (error) {
      setErro(error instanceof Error ? error.message : 'Erro inesperado.')
    } finally {
      setSalvando(false)
    }
  }

  async function changeRole(id: number, nextRole: UserRole) {
    setErro(null)
    const response = await apiFetch(`${API_BASE}/api/admin/usuarios/${id}/perfil`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ perfil: nextRole }),
    })
    if (!response.ok) {
      setErro(await responseError(response, 'Não foi possível alterar a permissão.'))
      return
    }
    await loadUsers()
  }

  async function deleteUser(user: AuthUser) {
    if (!window.confirm(`Excluir o usuário “${user.login}”? Esta ação não pode ser desfeita.`)) return
    setErro(null)
    const response = await apiFetch(`${API_BASE}/api/admin/usuarios/${user.id}`, {
      method: 'DELETE',
    })
    if (!response.ok) {
      setErro(await responseError(response, 'Não foi possível excluir o usuário.'))
      return
    }
    await loadUsers()
  }

  return (
    <div className="modal-overlay admin-overlay" role="presentation" onMouseDown={onClose}>
      <section
        className="admin-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button ref={closeButton} type="button" className="modal-close" onClick={onClose} aria-label="Fechar gestão de usuários">×</button>
        <header className="admin-heading">
          <p>ADMINISTRAÇÃO</p>
          <h2 id="admin-title">Usuários e permissões</h2>
          <span>Crie acessos e defina quem pode administrar o WikiLaw.</span>
        </header>

        <form className="new-user-form" onSubmit={(event) => void createUser(event)}>
          <h3>Novo usuário</h3>
          <div className="admin-form-grid">
            <label>Login<input value={login} onChange={(event) => setLogin(event.target.value)} minLength={3} required /></label>
            <label>Senha<input type="password" value={senha} onChange={(event) => setSenha(event.target.value)} minLength={8} required /></label>
            <label>Permissão<select value={perfil} onChange={(event) => setPerfil(event.target.value as UserRole)}><option value="USER">Usuário</option><option value="ADMIN">Administrador</option></select></label>
            <button type="submit" disabled={salvando}>{salvando ? 'CRIANDO…' : 'CRIAR USUÁRIO'}</button>
          </div>
        </form>

        {erro && <p className="admin-error" role="alert">{erro}</p>}
        <div className="users-section">
          <div className="users-title"><h3>Contas cadastradas</h3><span>{users.length} {users.length === 1 ? 'conta' : 'contas'}</span></div>
          {carregando ? <p className="admin-loading">Carregando usuários…</p> : (
            <div className="users-list">
              {users.map((user) => {
                const isSelf = user.id === currentUser.id
                return (
                  <div className="user-row" key={user.id}>
                    <div className="user-avatar" aria-hidden="true">{user.login.charAt(0).toUpperCase()}</div>
                    <div className="user-identity"><strong>{user.login}</strong>{isSelf && <span>Você</span>}</div>
                    <label className="role-field"><span className="sr-only">Permissão de {user.login}</span><select value={user.perfil} disabled={isSelf} onChange={(event) => void changeRole(user.id, event.target.value as UserRole)}><option value="USER">Usuário</option><option value="ADMIN">Administrador</option></select></label>
                    <button type="button" className="delete-user" disabled={isSelf} onClick={() => void deleteUser(user)} aria-label={`Excluir ${user.login}`} title={isSelf ? 'A conta em uso não pode ser excluída' : 'Excluir usuário'}>
                      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3m-8 0 1 13h8l1-13M10 11v5m4-5v5" /></svg>
                    </button>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </section>
    </div>
  )
}
