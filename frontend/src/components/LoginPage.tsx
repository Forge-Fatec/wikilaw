import { useState, type FormEvent } from 'react'
import { signIn, type AuthUser } from '../auth'

export function LoginPage({ onLogin }: { onLogin: (user: AuthUser) => void }) {
  const [login, setLogin] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setErro(null)
    setEnviando(true)
    try {
      onLogin(await signIn(login, senha))
    } catch (error) {
      setErro(error instanceof Error ? error.message : 'Não foi possível entrar.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-panel" aria-labelledby="login-title">
        <div className="login-brand">
          <div className="brand-mark">W</div>
          <div className="brand-text">
            <div className="name">Wikilaw</div>
            <div className="tag">PESQUISA JURÍDICA</div>
          </div>
        </div>
        <div className="login-content">
          <div className="login-rule" />
          <p className="login-eyebrow">ACESSO RESTRITO</p>
          <h1 id="login-title">Bem-vindo de volta.</h1>
          <p className="login-intro">Entre para acessar a pesquisa jurídica do WikiLaw.</p>

          <form className="login-form" onSubmit={(event) => void submit(event)}>
            <label htmlFor="login">Login</label>
            <input
              id="login"
              value={login}
              onChange={(event) => setLogin(event.target.value)}
              autoComplete="username"
              autoFocus
              required
            />
            <label htmlFor="senha">Senha</label>
            <input
              id="senha"
              type="password"
              value={senha}
              onChange={(event) => setSenha(event.target.value)}
              autoComplete="current-password"
              required
            />
            {erro && <p className="login-error" role="alert">{erro}</p>}
            <button type="submit" disabled={enviando}>
              {enviando ? 'ENTRANDO…' : 'ENTRAR'}
            </button>
          </form>
        </div>
      </section>
      <aside className="login-visual" aria-hidden="true">
        <div className="login-visual-copy">
          <span>Pesquisa.</span>
          <span>Contexto.</span>
          <em>Decisão.</em>
        </div>
      </aside>
    </main>
  )
}
