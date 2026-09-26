import { useState, useMemo, useEffect, useCallback } from 'react'
import './index.css'

type DocType = 'jurisprudencia' | 'precedente' | 'doutrina'
type Ordenacao = 'mais-recentes' | 'mais-antigos'

const CATEGORIA_POR_TIPO: Record<DocType, string> = {
  jurisprudencia: 'decisoes',
  precedente: 'precedentes',
  doutrina: 'doutrina',
}

const BADGE_POR_TIPO: Record<DocType, string> = {
  jurisprudencia: 'JURISPRUDÊNCIA',
  precedente: 'PRECEDENTE',
  doutrina: 'DOUTRINA',
}

const ITENS_POR_PAGINA = 10

// Ajuste aqui se o back rodar em outra porta/host.
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
const ERRO_CARREGAMENTO =
  'Não foi possível carregar os resultados. Verifique se o back-end está rodando em ' +
  API_BASE +
  '.'

interface ApiSummary {
  id: number
  fonte: string
  categoria: string
  titulo: string
  tipoDocumento: string | null
  numeroProcessoOuTema: string | null
  autoresOuRelator: string | null
  resumoOuEmenta: string | null
  decisao: string | null
  tribunal: string | null
  orgaoJulgador: string | null
  dataJulgamento: string | null
  dataPublicacao: string | null
  dataOriginal: string | null
  urlOriginal: string | null
  idRegistroBruto: number | null
}

interface ApiResult {
  itens: ApiSummary[]
  pagina: number
  tamanho: number
  total: number
  totalPaginas: number
}

interface Documento {
  id: number
  type: DocType
  badge: string
  fonte: string
  title: string
  tipoDocumento: string | null
  numeroProcessoOuTema: string | null
  autoresOuRelator: string | null
  text: string
  decisao: string | null
  tribunal: string | null
  orgaoJulgador: string | null
  dataJulgamento: string | null
  dataPublicacao: string | null
  dataOriginal: string | null
  urlOriginal: string | null
}

function formatarData(dataPublicacao: string | null): string {
  if (!dataPublicacao) return 'Data não informada'
  const d = new Date(dataPublicacao)
  if (Number.isNaN(d.getTime())) return dataPublicacao
  return d.toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  })
}

function toDocumento(tipo: DocType, item: ApiSummary): Documento {
  return {
    id: item.id,
    type: tipo,
    badge: BADGE_POR_TIPO[tipo],
    fonte: item.fonte,
    title: item.titulo,
    tipoDocumento: item.tipoDocumento,
    numeroProcessoOuTema: item.numeroProcessoOuTema,
    autoresOuRelator: item.autoresOuRelator,
    text: item.resumoOuEmenta ?? 'Sem resumo disponível.',
    decisao: item.decisao,
    tribunal: item.tribunal,
    orgaoJulgador: item.orgaoJulgador,
    dataJulgamento: item.dataJulgamento,
    dataPublicacao: item.dataPublicacao,
    dataOriginal: item.dataOriginal,
    urlOriginal: item.urlOriginal,
  }
}

async function carregarDocumentos(termo: string): Promise<Documento[]> {
  const termoParam = termo.trim()
    ? `&termo=${encodeURIComponent(termo.trim())}`
    : ''

  const respostas = await Promise.all(
    (Object.keys(CATEGORIA_POR_TIPO) as DocType[]).map(async (tipo) => {
      const categoria = CATEGORIA_POR_TIPO[tipo]
      const res = await fetch(
        `${API_BASE}/api/documentos/${categoria}?tamanho=50${termoParam}`,
      )
      if (!res.ok) {
        throw new Error(`Erro ao buscar ${categoria} (status ${res.status})`)
      }
      const data: ApiResult = await res.json()
      return data.itens.map((item) => toDocumento(tipo, item))
    }),
  )

  return respostas.flat()
}

function App() {
  const [query, setQuery] = useState('')
  const [erroValidacao, setErroValidacao] = useState<string | null>(null)
  const [filtros, setFiltros] = useState<Record<DocType, boolean>>({
    jurisprudencia: true,
    precedente: true,
    doutrina: true,
  })
  const [tribunal, setTribunal] = useState('Todos os tribunais')
  const [dataDe, setDataDe] = useState('')
  const [dataAte, setDataAte] = useState('')
  const [ordenacao, setOrdenacao] = useState<Ordenacao>('mais-recentes')

  const [documentos, setDocumentos] = useState<Documento[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [paginaAtual, setPaginaAtual] = useState(1)
  const [documentoSelecionado, setDocumentoSelecionado] =
    useState<Documento | null>(null)

  const buscar = useCallback(async () => {
    if (!query.trim()) {
      setErroValidacao('Informe a descrição do caso para realizar a pesquisa.')
      return
    }

    setErroValidacao(null)
    setCarregando(true)
    setErro(null)
    setPaginaAtual(1)
    setDocumentoSelecionado(null)
    try {
      setDocumentos(await carregarDocumentos(query))
    } catch {
      setErro(ERRO_CARREGAMENTO)
      setDocumentos([])
    } finally {
      setCarregando(false)
    }
  }, [query])

  // Carrega os resultados iniciais (sem termo) assim que a página abre.
  useEffect(() => {
    let ativo = true

    carregarDocumentos('')
      .then((resultados) => {
        if (ativo) setDocumentos(resultados)
      })
      .catch(() => {
        if (ativo) {
          setErro(ERRO_CARREGAMENTO)
          setDocumentos([])
        }
      })
      .finally(() => {
        if (ativo) setCarregando(false)
      })

    return () => {
      ativo = false
    }
  }, [])

  useEffect(() => {
    if (!documentoSelecionado) return

    const overflowAnterior = document.body.style.overflow
    const fecharComEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setDocumentoSelecionado(null)
    }

    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', fecharComEscape)

    return () => {
      document.body.style.overflow = overflowAnterior
      document.removeEventListener('keydown', fecharComEscape)
    }
  }, [documentoSelecionado])

  const tribunaisDisponiveis = useMemo(() => {
    const siglas = new Set(
      documentos.map((d) => d.tribunal).filter((t): t is string => Boolean(t)),
    )
    return Array.from(siglas).sort()
  }, [documentos])

  const resultados = useMemo(() => {
    const filtrados = documentos.filter((doc) => {
      if (!filtros[doc.type]) return false

      if (tribunal !== 'Todos os tribunais' && doc.tribunal !== tribunal) {
        return false
      }

      if ((dataDe || dataAte) && doc.dataPublicacao) {
        const docDate = new Date(doc.dataPublicacao)
        if (dataDe && docDate < new Date(dataDe)) return false
        if (dataAte && docDate > new Date(dataAte)) return false
      }

      return true
    })

    return filtrados.sort((a, b) => {
      const dataA = a.dataPublicacao
        ? new Date(a.dataPublicacao).getTime()
        : NaN
      const dataB = b.dataPublicacao
        ? new Date(b.dataPublicacao).getTime()
        : NaN
      const aSemData = Number.isNaN(dataA)
      const bSemData = Number.isNaN(dataB)

      if (aSemData || bSemData) {
        return Number(aSemData) - Number(bSemData)
      }

      return ordenacao === 'mais-recentes' ? dataB - dataA : dataA - dataB
    })
  }, [documentos, filtros, tribunal, dataDe, dataAte, ordenacao])

  const totalPaginas = Math.max(
    1,
    Math.ceil(resultados.length / ITENS_POR_PAGINA),
  )
  const paginaExibida = Math.min(paginaAtual, totalPaginas)
  const resultadosPaginados = resultados.slice(
    (paginaExibida - 1) * ITENS_POR_PAGINA,
    paginaExibida * ITENS_POR_PAGINA,
  )

  function voltarParaPrimeiraPagina() {
    setPaginaAtual(1)
    setDocumentoSelecionado(null)
  }

  function toggleFiltro(type: DocType) {
    setFiltros((prev) => ({ ...prev, [type]: !prev[type] }))
    voltarParaPrimeiraPagina()
  }

  function limparFiltros() {
    setFiltros({ jurisprudencia: true, precedente: true, doutrina: true })
    setTribunal('Todos os tribunais')
    setDataDe('')
    setDataAte('')
    voltarParaPrimeiraPagina()
  }

  return (
    <>
      <header className="header">
        <div className="brand">
          <div className="brand-mark">L</div>
          <div className="brand-text">
            <div className="name">
              Lumen <em>Juris</em>
            </div>
            <div className="tag">PESQUISA JURÍDICA</div>
          </div>
        </div>
        <nav>
          <a href="#" className="active">
            JURISPRUDÊNCIA
          </a>
          <a href="#">PRECEDENTES</a>
          <a href="#">DOUTRINA</a>
        </nav>
      </header>

      <section className="hero">
        <div className="hero-rule" />
        <h1>
          Descreva o seu caso.{' '}
          <em>
            Nós
            <br />
            encontramos o direito.
          </em>
        </h1>
        <p>
          Pesquisa em linguagem natural entre jurisprudências, precedentes e
          doutrinas dos principais tribunais do país.
        </p>
        <form
          className="search-form"
          onSubmit={(event) => {
            event.preventDefault()
            void buscar()
          }}
        >
          <div className="search-box">
            <input
              type="text"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value)
                if (erroValidacao) setErroValidacao(null)
              }}
              aria-label="Descrição do caso"
              aria-invalid={Boolean(erroValidacao)}
              aria-describedby={erroValidacao ? 'search-error' : undefined}
              placeholder="Ex.: dano moral por negativação indevida do nome do consumidor..."
            />
            <button type="submit" disabled={carregando}>
              {carregando ? 'BUSCANDO...' : '🔍 PESQUISAR'}
            </button>
          </div>
          {erroValidacao && (
            <p id="search-error" className="search-error" role="alert">
              {erroValidacao}
            </p>
          )}
        </form>
      </section>

      <div className="layout">
        <aside className="filters">
          <h3>▤ Filtros</h3>

          <div className="group doc-types">
            <div className="group-label">TIPO DE DOCUMENTO</div>
            <div className="check-row">
              <label className="check">
                <input
                  type="checkbox"
                  checked={filtros.jurisprudencia}
                  onChange={() => toggleFiltro('jurisprudencia')}
                />
                Jurisprudência
              </label>
              <label className="check">
                <input
                  type="checkbox"
                  checked={filtros.precedente}
                  onChange={() => toggleFiltro('precedente')}
                />
                Precedente
              </label>
              <label className="check">
                <input
                  type="checkbox"
                  checked={filtros.doutrina}
                  onChange={() => toggleFiltro('doutrina')}
                />
                Doutrina
              </label>
            </div>
          </div>

          <div className="group">
            <div className="group-label">TRIBUNAL</div>
            <select
              value={tribunal}
              onChange={(e) => {
                setTribunal(e.target.value)
                voltarParaPrimeiraPagina()
              }}
            >
              <option>Todos os tribunais</option>
              {tribunaisDisponiveis.map((sigla) => (
                <option key={sigla}>{sigla}</option>
              ))}
            </select>
          </div>

          <div className="group">
            <div className="group-label">PERÍODO</div>
            <div className="date-row">
              <div>
                <label>De</label>
                <input
                  className="date-input"
                  type="date"
                  value={dataDe}
                  onChange={(e) => {
                    setDataDe(e.target.value)
                    voltarParaPrimeiraPagina()
                  }}
                />
              </div>
              <div>
                <label>Até</label>
                <input
                  className="date-input"
                  type="date"
                  value={dataAte}
                  onChange={(e) => {
                    setDataAte(e.target.value)
                    voltarParaPrimeiraPagina()
                  }}
                />
              </div>
            </div>
          </div>

          <button className="clear-btn" onClick={limparFiltros}>
            LIMPAR FILTROS
          </button>
        </aside>

        <main>
          <div className="results-header">
            <div className="results-summary">
              <h2>Resultados da pesquisa</h2>
              <span className="results-count">
                {carregando
                  ? 'Buscando...'
                  : `${resultados.length} documentos encontrados · Página ${paginaExibida} de ${totalPaginas}`}
              </span>
            </div>
            <div className="sort-control" role="group" aria-label="Ordenar resultados por data de publicação">
              <span className="sort-label">ORDENAR POR DATA</span>
              <div className="sort-options">
                <button
                  type="button"
                  aria-pressed={ordenacao === 'mais-recentes'}
                  onClick={() => {
                    setOrdenacao('mais-recentes')
                    voltarParaPrimeiraPagina()
                  }}
                >
                  Mais recentes
                </button>
                <button
                  type="button"
                  aria-pressed={ordenacao === 'mais-antigos'}
                  onClick={() => {
                    setOrdenacao('mais-antigos')
                    voltarParaPrimeiraPagina()
                  }}
                >
                  Mais antigos
                </button>
              </div>
            </div>
          </div>

          {erro && <div className="empty">{erro}</div>}

          {!erro && !carregando && resultados.length === 0 && (
            <div className="empty">
              Nenhum documento encontrado com os filtros selecionados.
            </div>
          )}

          {!erro &&
            resultadosPaginados.map((doc) => {
              const chave = `${doc.type}-${doc.id}`

              return (
                <article className="card" key={chave}>
                  <div className="card-top">
                    <span
                      className={
                        'badge' +
                        (doc.type === 'precedente' ? ' precedente' : '') +
                        (doc.type === 'doutrina' ? ' doutrina' : '')
                      }
                    >
                      {doc.badge}
                    </span>
                    <span className="proc-num">{doc.fonte}</span>
                  </div>
                  <h3>{doc.title}</h3>
                  {doc.type === 'jurisprudencia' && (
                    <div className="decision-highlight">
                      <span className="decision-label">DECISÃO</span>
                      <strong>
                        {doc.decisao ?? 'Decisão não informada pela fonte.'}
                      </strong>
                    </div>
                  )}
                  <div className="document-content">
                    <h4>Ementa</h4>
                    <p className="ementa">{doc.text}</p>
                  </div>
                  <button
                    type="button"
                    className="details-toggle"
                    aria-haspopup="dialog"
                    onClick={() => setDocumentoSelecionado(doc)}
                  >
                    VER DETALHES
                  </button>
                  <div className="card-bottom">
                    <div className="card-meta">
                      <span>
                        ⚖{' '}
                        {[doc.tribunal, doc.orgaoJulgador]
                          .filter(Boolean)
                          .join(' — ') || doc.fonte}
                      </span>
                      <span>🗓 {formatarData(doc.dataPublicacao)}</span>
                    </div>
                  </div>
                </article>
              )
            })}

          {!erro && !carregando && totalPaginas > 1 && (
            <div
              className="pagination"
              role="navigation"
              aria-label="Páginas de resultados"
            >
              <button
                type="button"
                disabled={paginaExibida === 1}
                onClick={() => {
                  setPaginaAtual((pagina) => Math.max(1, pagina - 1))
                  setDocumentoSelecionado(null)
                }}
              >
                ANTERIOR
              </button>
              {Array.from({ length: totalPaginas }, (_, index) => index + 1).map(
                (pagina) => (
                  <button
                    type="button"
                    className={pagina === paginaExibida ? 'active' : ''}
                    aria-current={pagina === paginaExibida ? 'page' : undefined}
                    key={pagina}
                    onClick={() => {
                      setPaginaAtual(pagina)
                      setDocumentoSelecionado(null)
                    }}
                  >
                    {pagina}
                  </button>
                ),
              )}
              <button
                type="button"
                disabled={paginaExibida === totalPaginas}
                onClick={() => {
                  setPaginaAtual((pagina) =>
                    Math.min(totalPaginas, pagina + 1),
                  )
                  setDocumentoSelecionado(null)
                }}
              >
                PRÓXIMA
              </button>
            </div>
          )}
        </main>
      </div>

      {documentoSelecionado && (
        <div
          className="modal-overlay"
          role="presentation"
          onMouseDown={() => setDocumentoSelecionado(null)}
        >
          <section
            className="details-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="modal-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              className="modal-close"
              aria-label="Fechar detalhes"
              autoFocus
              onClick={() => setDocumentoSelecionado(null)}
            >
              ×
            </button>

            <div className="modal-heading">
              <span
                className={
                  'badge' +
                  (documentoSelecionado.type === 'precedente'
                    ? ' precedente'
                    : '') +
                  (documentoSelecionado.type === 'doutrina' ? ' doutrina' : '')
                }
              >
                {documentoSelecionado.badge}
              </span>
              <span className="proc-num">{documentoSelecionado.fonte}</span>
              <h2 id="modal-title">{documentoSelecionado.title}</h2>
            </div>

            <dl className="details-grid">
              {documentoSelecionado.tipoDocumento && (
                <div>
                  <dt>Tipo</dt>
                  <dd>{documentoSelecionado.tipoDocumento}</dd>
                </div>
              )}
              {documentoSelecionado.numeroProcessoOuTema && (
                <div>
                  <dt>
                    {documentoSelecionado.type === 'precedente'
                      ? 'Tema'
                      : 'Processo'}
                  </dt>
                  <dd>{documentoSelecionado.numeroProcessoOuTema}</dd>
                </div>
              )}
              <div>
                <dt>Fonte</dt>
                <dd>{documentoSelecionado.fonte}</dd>
              </div>
              {documentoSelecionado.tribunal && (
                <div>
                  <dt>Tribunal</dt>
                  <dd>{documentoSelecionado.tribunal}</dd>
                </div>
              )}
              {documentoSelecionado.orgaoJulgador && (
                <div>
                  <dt>Órgão julgador</dt>
                  <dd>{documentoSelecionado.orgaoJulgador}</dd>
                </div>
              )}
              {documentoSelecionado.autoresOuRelator && (
                <div>
                  <dt>
                    {documentoSelecionado.type === 'doutrina'
                      ? 'Autores'
                      : 'Relator'}
                  </dt>
                  <dd>{documentoSelecionado.autoresOuRelator}</dd>
                </div>
              )}
              {documentoSelecionado.dataJulgamento && (
                <div>
                  <dt>Data do julgamento</dt>
                  <dd>{formatarData(documentoSelecionado.dataJulgamento)}</dd>
                </div>
              )}
              <div>
                <dt>Data da publicação</dt>
                <dd>{formatarData(documentoSelecionado.dataPublicacao)}</dd>
              </div>
              {documentoSelecionado.dataOriginal && (
                <div>
                  <dt>Data informada pela fonte</dt>
                  <dd>{documentoSelecionado.dataOriginal}</dd>
                </div>
              )}
            </dl>

            {documentoSelecionado.type === 'jurisprudencia' && (
              <div className="modal-decision">
                <span className="decision-label">DECISÃO</span>
                <p>
                  {documentoSelecionado.decisao ??
                    'Decisão não informada pela fonte.'}
                </p>
              </div>
            )}

            <div className="modal-summary">
              <h3>Ementa</h3>
              <p>{documentoSelecionado.text}</p>
            </div>

            {documentoSelecionado.urlOriginal && (
              <footer className="modal-footer">
                <a
                  href={documentoSelecionado.urlOriginal}
                  target="_blank"
                  rel="noreferrer"
                  className="source-button"
                >
                  IR PARA A FONTE ORIGINAL ↗
                </a>
              </footer>
            )}
          </section>
        </div>
      )}
    </>
  )
}

export default App
