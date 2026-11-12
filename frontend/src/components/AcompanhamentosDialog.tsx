import { useEffect, useRef, useState } from 'react'
import type { PesquisaAcompanhada } from '../acompanhamentos'

const ROTULO_CATEGORIA: Record<string, string> = {
  decisoes: 'Jurisprudência',
  precedentes: 'Precedente',
  doutrina: 'Doutrina',
}

function formatarDataHora(data: string): string {
  return new Date(data).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
}

export function AcompanhamentosDialog({
  lista,
  verificando,
  onVerificar,
  onMarcarComoVistas,
  onDeixarDeAcompanhar,
  onClose,
}: {
  lista: PesquisaAcompanhada[]
  verificando: boolean
  onVerificar: () => void
  onMarcarComoVistas: (id: string) => Promise<void>
  onDeixarDeAcompanhar: (id: string) => void
  onClose: () => void
}) {
  const closeButton = useRef<HTMLButtonElement>(null)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    closeButton.current?.focus()
    function escape(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', escape)
    return () => window.removeEventListener('keydown', escape)
  }, [onClose])

  async function marcarComoVistas(id: string) {
    setErro(null)
    try {
      await onMarcarComoVistas(id)
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : 'Não foi possível marcar como vistas.')
    }
  }

  return (
    <div className="modal-overlay" role="presentation" onMouseDown={onClose}>
      <section
        className="admin-modal acompanhamentos-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="acompanhamentos-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button ref={closeButton} type="button" className="modal-close" onClick={onClose} aria-label="Fechar pesquisas acompanhadas">×</button>
        <header className="admin-heading">
          <p>ACOMPANHAMENTO</p>
          <h2 id="acompanhamentos-title">Pesquisas acompanhadas</h2>
          <span>
            Documentos que entraram no acervo depois que você começou a acompanhar e que se parecem com o caso.
            Fica salvo só neste navegador.
          </span>
        </header>

        <div className="acompanhamentos-acoes">
          <button type="button" className="acompanhamentos-verificar" onClick={onVerificar} disabled={verificando || !lista.length}>
            {verificando ? 'Verificando...' : 'Verificar agora'}
          </button>
        </div>
        {erro && <p className="admin-error" role="alert">{erro}</p>}

        {!lista.length && (
          <p className="acompanhamentos-vazio">
            Nenhuma pesquisa acompanhada. Faça uma pesquisa por similaridade e clique em “Acompanhar esta pesquisa”.
          </p>
        )}

        <ul className="acompanhamentos-lista">
          {lista.map((pesquisa) => (
            <li key={pesquisa.id} className="acompanhamento">
              <div className="acompanhamento-topo">
                <h3>{pesquisa.descricao || pesquisa.documento || 'Pesquisa sem descrição'}</h3>
                <span className={'acompanhamento-contador' + (pesquisa.novidades.length ? ' tem-novidades' : '')}>
                  {pesquisa.novidades.length === 1 ? '1 novo' : `${pesquisa.novidades.length} novos`}
                </span>
              </div>
              <p className="acompanhamento-meta">
                {pesquisa.documento && <>Documento: {pesquisa.documento} · </>}
                Acompanhando desde {formatarDataHora(pesquisa.criadaEm)}
                {pesquisa.verificadaEm && <> · Verificada em {formatarDataHora(pesquisa.verificadaEm)}</>}
              </p>
              {pesquisa.erro && <p className="acompanhamento-erro" role="status">{pesquisa.erro}</p>}

              {pesquisa.novidades.length > 0 && (
                <ul className="acompanhamento-novidades">
                  {pesquisa.novidades.map((novidade) => (
                    <li key={`${novidade.categoria}-${novidade.id}`}>
                      <div className="acompanhamento-novidade-topo">
                        <span className="badge">{ROTULO_CATEGORIA[novidade.categoria] ?? novidade.categoria}</span>
                        <span className="similaridade-selo">{novidade.percentual}% similar</span>
                      </div>
                      <strong>{novidade.titulo}</strong>
                      <span className="acompanhamento-origem">{[novidade.tribunal, novidade.fonte].filter(Boolean).join(' · ')}</span>
                      <p className="trecho-similar-justificativa">{novidade.justificativa}</p>
                      <blockquote>{novidade.trecho}</blockquote>
                    </li>
                  ))}
                </ul>
              )}

              <div className="acompanhamento-botoes">
                {pesquisa.novidades.length > 0 && (
                  <button type="button" onClick={() => void marcarComoVistas(pesquisa.id)}>Marcar como vistas</button>
                )}
                <button type="button" className="acompanhamento-remover" onClick={() => onDeixarDeAcompanhar(pesquisa.id)}>
                  Deixar de acompanhar
                </button>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
