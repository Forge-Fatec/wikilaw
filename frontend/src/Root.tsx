import { useCallback, useEffect, useState } from 'react'
import App from './App'
import { currentUser, signOut, type AuthUser } from './auth'
import { AdminUsersDialog } from './components/AdminUsersDialog'
import { LoginPage } from './components/LoginPage'

export function Root() {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)
  const [adminOpen, setAdminOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const clearSession = useCallback(() => {
    setUser(null)
    setAdminOpen(false)
  }, [])

  useEffect(() => {
    void currentUser()
      .then(setUser)
      .catch((reason) => setError(reason instanceof Error ? reason.message : 'Não foi possível abrir o WikiLaw.'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    window.addEventListener('wikilaw:unauthorized', clearSession)
    return () => window.removeEventListener('wikilaw:unauthorized', clearSession)
  }, [clearSession])

  async function logout() {
    try {
      await signOut()
    } finally {
      clearSession()
    }
  }

  if (loading) return <div className="session-loading"><div className="brand-mark">W</div><span>Carregando WikiLaw…</span></div>
  if (!user) return <><LoginPage onLogin={(authenticated) => { setError(null); setUser(authenticated) }} />{error && <p className="startup-error" role="alert">{error}</p>}</>

  return (
    <>
      <App user={user} onLogout={() => void logout()} onOpenAdmin={() => setAdminOpen(true)} />
      {adminOpen && user.perfil === 'ADMIN' && <AdminUsersDialog currentUser={user} onClose={() => setAdminOpen(false)} />}
    </>
  )
}
