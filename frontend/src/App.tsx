import { useState, useMemo, useEffect, useCallback } from 'react'
import './index.css'

type DocType = 'jurisprudencia' | 'precedente' | 'doutrina'

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

// Ajuste aqui se o back rodar em outra porta/host.
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

interface ApiSummary {
  id: number
  fonte: string
  categoria: string
  titulo: string
  autoresOuRelator: string | null
  resumoOuEmenta: string | null
  tribunal: string | null
  orgaoJulgador: string | null
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
  type: DocType
  badge: string
  fonte: string
  title: string
  text: string
  tribunal: string | null
  orgaoJulgador: string | null
  dataPublicacao: string | null
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
    type: tipo,
    badge: BADGE_POR_TIPO[tipo],
    fonte: item.fonte,
    title: item.titulo,
    text: item.resumoOuEmenta ?? 'Sem resumo disponível.',
    tribunal: item.tribunal,
    orgaoJulgador: item.orgaoJulgador,
    dataPublicacao: item.dataPublicacao,
    urlOriginal: item.urlOriginal,
  }
}

function App() {
  const [query, setQuery] = useState('')
  const [filtros, setFiltros] = useState<Record<DocType, boolean>>({
    jurisprudencia: true,
    precedente: true,
    doutrina: true,
  })
  const [tribunal, setTribunal] = useState('Todos os tribunais')
  const [dataDe, setDataDe] = useState('')
  const [dataAte, setDataAte] = useState('')

  const [documentos, setDocumentos] = useState<Documento[]>([])
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const buscar = useCallback(async () => {
    setCarregando(true)
    setErro(null)
    try {
      const termoParam = query.trim()
        ? `&termo=${encodeURIComponent(query.trim())}`
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

      setDocumentos(respostas.flat())
    } catch {
      setErro(
        'Não foi possível carregar os resultados. Verifique se o back-end está rodando em ' +
          API_BASE +
          '.',
      )
      setDocumentos([])
    } finally {
      setCarregando(false)
    }
  }, [query])

  // Carrega os resultados iniciais (sem termo) assim que a página abre.
  useEffect(() => {
    buscar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const tribunaisDisponiveis = useMemo(() => {
    const siglas = new Set(
      documentos.map((d) => d.tribunal).filter((t): t is string => Boolean(t)),
    )
    return Array.from(siglas).sort()
  }, [documentos])

  const resultados = useMemo(() => {
    return documentos.filter((doc) => {
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
  }, [documentos, filtros, tribunal, dataDe, dataAte])

  function toggleFiltro(type: DocType) {
    setFiltros((prev) => ({ ...prev, [type]: !prev[type] }))
  }

  function limparFiltros() {
    setFiltros({ jurisprudencia: true, precedente: true, doutrina: true })
    setTribunal('Todos os tribunais')
    setDataDe('')
    setDataAte('')
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
        <div className="search-box">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyUp={(e) => e.key === 'Enter' && buscar()}
            placeholder="Ex.: dano moral por negativação indevida do nome do consumidor..."
          />
          <button onClick={buscar} disabled={carregando}>
            {carregando ? 'BUSCANDO...' : '🔍 PESQUISAR'}
          </button>
        </div>
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
              onChange={(e) => setTribunal(e.target.value)}
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
                  onChange={(e) => setDataDe(e.target.value)}
                />
              </div>
              <div>
                <label>Até</label>
                <input
                  className="date-input"
                  type="date"
                  value={dataAte}
                  onChange={(e) => setDataAte(e.target.value)}
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
            <div>
              <h2>Resultados da pesquisa</h2>
              <span>
                {carregando
                  ? 'Buscando...'
                  : `${resultados.length} documentos encontrados`}
              </span>
            </div>
            <span>📄</span>
          </div>

          {erro && <div className="empty">{erro}</div>}

          {!erro && !carregando && resultados.length === 0 && (
            <div className="empty">
              Nenhum documento encontrado com os filtros selecionados.
            </div>
          )}

          {!erro &&
            resultados.map((doc, i) => (
              <article className="card" key={`${doc.type}-${i}`}>
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
                <p>{doc.text}</p>
                <div className="card-bottom">
                  <div className="card-meta">
                    <span>
                      ⚖ {[doc.tribunal, doc.orgaoJulgador].filter(Boolean).join(' — ') || doc.fonte}
                    </span>
                    <span>🗓 {formatarData(doc.dataPublicacao)}</span>
                  </div>
                  {doc.urlOriginal && (
                    <a href={doc.urlOriginal} target="_blank" rel="noreferrer" className="fonte">
                      FONTE ORIGINAL ↗
                    </a>
                  )}
                </div>
              </article>
            ))}
        </main>
      </div>
    </>
  )
}

export default App