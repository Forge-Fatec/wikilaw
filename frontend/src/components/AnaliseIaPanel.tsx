import { useEffect, useRef, useState } from 'react'
import {
  gerarAnaliseJuridica,
  MAX_DOCUMENTOS_ANALISE,
  type AnaliseJuridica,
  type ReferenciaDocumento,
} from '../analiseIa'

interface Props {
  /** Resultados na ordem exibida; só os primeiros MAX_DOCUMENTOS_ANALISE são analisados. */
  resultados: ReferenciaDocumento[]
  termo: string
  onAbrirDocumento: (referencia: ReferenciaDocumento) => void
}

type Estado =
  | { fase: 'inicial' }
  | { fase: 'gerando' }
  | { fase: 'erro'; mensagem: string }
  | { fase: 'pronta'; analise: AnaliseJuridica }

const horario = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

export function AnaliseIaPanel({ resultados, termo, onAbrirDocumento }: Props) {
  const [estado, setEstado] = useState<Estado>({ fase: 'inicial' })
  const requisicao = useRef<AbortController | null>(null)
  const titulo = useRef<HTMLHeadingElement>(null)
  const analisados = resultados.slice(0, MAX_DOCUMENTOS_ANALISE)

  useEffect(() => () => requisicao.current?.abort(), [])
  useEffect(() => {
    if (estado.fase === 'pronta' || estado.fase === 'erro') titulo.current?.focus()
  }, [estado.fase])

  async function analisar() {
    requisicao.current?.abort()
    const controle = new AbortController()
    requisicao.current = controle
    setEstado({ fase: 'gerando' })
    try {
      const analise = await gerarAnaliseJuridica(termo, analisados, controle.signal)
      if (!controle.signal.aborted) setEstado({ fase: 'pronta', analise })
    } catch (error) {
      if (controle.signal.aborted) return
      setEstado({
        fase: 'erro',
        mensagem: error instanceof Error ? error.message : 'Não foi possível gerar a análise. Tente novamente.',
      })
    }
  }

  const gerando = estado.fase === 'gerando'

  return (
    <section className="ai-analysis" aria-labelledby="ai-analysis-title" aria-busy={gerando}>
      <header className="ai-analysis-header">
        <div>
          <span className="ai-badge" aria-hidden="true">✦ IA</span>
          <h3 id="ai-analysis-title" ref={titulo} tabIndex={-1}>Análise jurídica com IA</h3>
          <p className="ai-analysis-scope">
            {estado.fase === 'pronta'
              ? `Baseada nos ${estado.analise.documentosAnalisados} primeiros resultados da pesquisa.`
              : `Analisa os ${analisados.length} primeiros resultados exibidos, na ordem atual.`}
          </p>
        </div>
        {estado.fase === 'inicial' && (
          <button type="button" className="ai-analysis-btn" onClick={() => void analisar()} disabled={!analisados.length}>
            Analisar com IA
          </button>
        )}
        {estado.fase === 'pronta' && (
          <button type="button" className="ai-analysis-btn secondary" onClick={() => void analisar()}>
            Gerar novamente
          </button>
        )}
      </header>

      {gerando && (
        <div className="ai-analysis-loading" role="status">
          <span className="ai-spinner" aria-hidden="true" />
          Gerando a análise com inteligência artificial. Isso pode levar alguns segundos…
        </div>
      )}

      {estado.fase === 'erro' && (
        <div className="ai-analysis-error" role="alert">
          <p>{estado.mensagem}</p>
          <button type="button" className="ai-analysis-btn" onClick={() => void analisar()}>
            Tentar novamente
          </button>
        </div>
      )}

      {estado.fase === 'pronta' && <ConteudoAnalise analise={estado.analise} onAbrirDocumento={onAbrirDocumento} />}
    </section>
  )
}

function ConteudoAnalise({ analise, onAbrirDocumento }: {
  analise: AnaliseJuridica
  onAbrirDocumento: (referencia: ReferenciaDocumento) => void
}) {
  const geradaEm = new Date(analise.geradaEm)
  return (
    <div className="ai-analysis-body">
      <p className="ai-disclaimer" role="note">
        <strong>Conteúdo gerado por inteligência artificial.</strong> {analise.aviso}
      </p>

      <h4>Resumo</h4>
      <p className="ai-summary">{analise.resumo}</p>

      {(analise.questaoJuridica || analise.entendimentoPredominante) && (
        <div className="ai-highlights">
          {analise.questaoJuridica && (
            <div className="ai-highlight">
              <span>Questão jurídica</span>
              <p>{analise.questaoJuridica}</p>
            </div>
          )}
          {analise.entendimentoPredominante && (
            <div className="ai-highlight strong">
              <span>Entendimento predominante</span>
              <p>{analise.entendimentoPredominante}</p>
            </div>
          )}
        </div>
      )}

      {analise.pontosPrincipais.length > 0 && (
        <>
          <h4>Pontos principais</h4>
          <ul className="ai-list">
            {analise.pontosPrincipais.map((ponto, i) => <li key={i}>{ponto}</li>)}
          </ul>
        </>
      )}

      {analise.fundamentosLegais.length > 0 && (
        <>
          <h4>Fundamentos legais citados</h4>
          <ul className="ai-chips">
            {analise.fundamentosLegais.map((fundamento, i) => <li key={i}>{fundamento}</li>)}
          </ul>
        </>
      )}

      {analise.divergencias.length > 0 && (
        <>
          <h4>Divergências</h4>
          <ul className="ai-list">
            {analise.divergencias.map((divergencia, i) => <li key={i}>{divergencia}</li>)}
          </ul>
        </>
      )}

      {analise.documentosRelevantes.length > 0 && (
        <>
          <h4>Documentos mais relevantes</h4>
          <ol className="ai-documents">
            {analise.documentosRelevantes.map((doc) => (
              <li key={`${doc.categoria}-${doc.id}`}>
                <button type="button" className="ai-document-link" onClick={() => onAbrirDocumento(doc)}>
                  {doc.titulo ?? 'Documento sem título'}
                </button>
                {doc.motivo && <p>{doc.motivo}</p>}
              </li>
            ))}
          </ol>
        </>
      )}

      {analise.limitacoes.length > 0 && (
        <>
          <h4>Limitações</h4>
          <ul className="ai-list muted">
            {analise.limitacoes.map((limitacao, i) => <li key={i}>{limitacao}</li>)}
          </ul>
        </>
      )}

      <p className="ai-meta">
        Gerada com auxílio de IA pelo modelo {analise.modelo}
        {Number.isNaN(geradaEm.getTime()) ? '' : ` em ${horario.format(geradaEm)}`}
        {' · '}Os números entre colchetes, como [1], indicam a posição do documento na lista de resultados.
      </p>
    </div>
  )
}
