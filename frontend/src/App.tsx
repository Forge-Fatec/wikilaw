import { useState, useMemo } from 'react'
import './index.css'

type DocType = 'jurisprudencia' | 'precedente' | 'doutrina'

interface Documento {
  type: DocType
  badge: string
  proc: string
  title: string
  text: string
  court: string
  date: string
}

const documentos: Documento[] = [
  {
    type: 'jurisprudencia',
    badge: 'JURISPRUDÊNCIA',
    proc: 'Apel. 1002345-67.2023.8.26.0100',
    title: 'Usucapião extraordinária urbana',
    text: 'Comprovada a posse mansa, pacífica e ininterrupta por mais de quinze anos sobre imóvel urbano, com ânimo de dona e sem oposição do titular registral, é cabível a declaração de usucapião extraordinária.',
    court: 'TJSP',
    date: '02 de junho de 2024',
  },
  {
    type: 'jurisprudencia',
    badge: 'RE',
    proc: 'RE 1.234.567/SP',
    title: 'Responsabilidade civil por abandono afetivo',
    text: 'O abandono afetivo dos pais em relação aos filhos, quando comprovado dano moral decorrente da omissão no dever de cuidado, pode gerar obrigação de indenizar, desde que presentes conduta, dano e nexo causal.',
    court: 'STJ',
    date: '12 de março de 2024',
  },
  {
    type: 'precedente',
    badge: 'PRECEDENTE',
    proc: 'Tema 1.061',
    title: 'Negativação indevida e dano moral presumido',
    text: 'A inscrição indevida do nome do consumidor em cadastros de inadimplentes gera dano moral in re ipsa, dispensada a comprovação de prejuízo efetivo, cabendo indenização proporcional à extensão do dano.',
    court: 'STJ',
    date: '28 de fevereiro de 2024',
  },
  {
    type: 'doutrina',
    badge: 'DOUTRINA',
    proc: 'Cap. 4 — Responsabilidade Civil',
    title: 'Elementos da responsabilidade civil objetiva',
    text: 'A doutrina majoritária reconhece que a responsabilidade objetiva prescinde da análise de culpa, bastando a comprovação do nexo causal entre a conduta do agente e o dano suportado pela vítima.',
    court: 'Doutrina',
    date: 'Edição 2023',
  },
]

function App() {
  const [query, setQuery] = useState(
    'dano moral por negativação indevida do nome do consumidor...',
  )
  const [filtros, setFiltros] = useState<Record<DocType, boolean>>({
    jurisprudencia: true,
    precedente: true,
    doutrina: true,
  })
  const [tribunal, setTribunal] = useState('Todos os tribunais')
  const [dataDe, setDataDe] = useState('')
  const [dataAte, setDataAte] = useState('')

  const resultados = useMemo(
    () => documentos.filter((doc) => filtros[doc.type]),
    [filtros],
  )

  function toggleFiltro(type: DocType) {
    setFiltros((prev) => ({ ...prev, [type]: !prev[type] }))
  }

  function limparFiltros() {
    setFiltros({ jurisprudencia: true, precedente: true, doutrina: true })
    setTribunal('Todos os tribunais')
    setDataDe('')
    setDataAte('')
  }

  function pesquisar() {
    // Placeholder: aqui entra a chamada para a API de pesquisa
    console.log('Pesquisando por:', query)
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
            onKeyUp={(e) => e.key === 'Enter' && pesquisar()}
            placeholder="Ex.: dano moral por negativação indevida do nome do consumidor..."
          />
          <button onClick={pesquisar}>🔍 PESQUISAR</button>
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
              <option>TJSP</option>
              <option>STJ</option>
              <option>STF</option>
              <option>TRF</option>
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
              <span>{resultados.length} documentos encontrados</span>
            </div>
            <span>📄</span>
          </div>

          {resultados.length === 0 && (
            <div className="empty">
              Nenhum documento encontrado com os filtros selecionados.
            </div>
          )}

          {resultados.map((doc) => (
            <article className="card" key={doc.proc}>
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
                <span className="proc-num">{doc.proc}</span>
              </div>
              <h3>{doc.title}</h3>
              <p>{doc.text}</p>
              <div className="card-bottom">
                <div className="card-meta">
                  <span>⚖ {doc.court}</span>
                  <span>🗓 {doc.date}</span>
                </div>
                <a href="#" className="fonte">
                  FONTE ORIGINAL ↗
                </a>
              </div>
            </article>
          ))}
        </main>
      </div>
    </>
  )
}

export default App