import { useEffect, useRef, useState } from 'react'
import {
  gerarAnaliseJuridica,
  MAX_RESULTADOS_ANALISE,
  type AnaliseJuridica,
  type PesquisaAtiva,
  type ReferenciaDocumento,
} from '../analiseIa'
import { FontesAnalise } from './FontesAnalise'
import { TextoComCitacoes } from './TextoComCitacoes'
import { ConversaAnalise } from './ConversaAnalise'

interface Props {
  /** Resultados da pesquisa ativa na ordem exibida; o back escolhe os mais relevantes entre eles. */
  resultados: ReferenciaDocumento[]
  /** Pesquisa exibida (modo, filtros e total), enviada junto para contextualizar a análise. */
  pesquisa: PesquisaAtiva
  termo: string
  onAbrirDocumento: (referencia: ReferenciaDocumento) => void
}

type Estado =
  | { fase: 'inicial' }
  | { fase: 'gerando' }
  | { fase: 'erro'; mensagem: string }
  | { fase: 'pronta'; analise: AnaliseJuridica }

const horario = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })

export function AnaliseIaPanel({ resultados, pesquisa, termo, onAbrirDocumento }: Props) {
  const [estado, setEstado] = useState<Estado>({ fase: 'inicial' })
  const requisicao = useRef<AbortController | null>(null)
  const titulo = useRef<HTMLHeadingElement>(null)
  const enviados = resultados.slice(0, MAX_RESULTADOS_ANALISE)

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
      const analise = await gerarAnaliseJuridica(termo, pesquisa, enviados, controle.signal)
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
              ? `Baseada em ${estado.analise.documentosAnalisados} dos ${pesquisa.totalResultados} resultados da pesquisa atual.`
              : `Usa a pesquisa atual (${pesquisa.totalResultados} resultados, com os filtros e a ordenação aplicados) e analisa os mais relevantes.`}
          </p>
        </div>
        {estado.fase === 'inicial' && (
          <button type="button" className="ai-analysis-btn" onClick={() => void analisar()} disabled={!enviados.length}>
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

      {estado.fase === 'pronta' && (
        <>
          <ConteudoAnalise analise={estado.analise} onAbrirDocumento={onAbrirDocumento} />
          {/* A chave recomeça a conversa quando a análise é gerada de novo. */}
          <ConversaAnalise
            key={estado.analise.geradaEm}
            termo={termo}
            pesquisa={pesquisa}
            resultados={enviados}
            onAbrirDocumento={onAbrirDocumento}
          />
        </>
      )}
    </section>
  )
}

/** Quais resultados da pesquisa entraram no contexto da IA, pela posição na lista. */
function ResultadosUtilizados({ analise }: { analise: AnaliseJuridica }) {
  const posicoes = (analise.resultadosUtilizados ?? []).map((resultado) => resultado.posicao)
  const omitidos = analise.resultadosOmitidos?.length ?? 0
  if (!posicoes.length) return null
  return (
    <p className="ai-context-scope">
      Resultados usados como contexto: {posicoes.length === 1 ? 'posição' : 'posições'} {posicoes.join(', ')}.
      {omitidos > 0 && ` ${omitidos} ${omitidos === 1 ? 'resultado ficou' : 'resultados ficaram'} de fora pelo limite da IA.`}
      {analise.contextoResumido && omitidos === 0 && ' Parte do conteúdo foi resumida para caber no limite da IA.'}
    </p>
  )
}

function ConteudoAnalise({ analise, onAbrirDocumento }: {
  analise: AnaliseJuridica
  onAbrirDocumento: (referencia: ReferenciaDocumento) => void
}) {
  const geradaEm = new Date(analise.geradaEm)
  // So as fontes verificadas pelo back viram link; uma citacao sem fonte fica como texto.
  const numerosFontes = new Set((analise.fontes ?? []).map((fonte) => fonte.numero))
  const citado = (texto: string) => <TextoComCitacoes texto={texto} fontes={numerosFontes} />
  return (
    <div className="ai-analysis-body">
      <p className="ai-disclaimer" role="note">
        <strong>Conteúdo gerado por inteligência artificial.</strong> {analise.aviso}
      </p>

      <h4>Resumo</h4>
      <p className="ai-summary">{citado(analise.resumo)}</p>

      {(analise.questaoJuridica || analise.entendimentoPredominante) && (
        <div className="ai-highlights">
          {analise.questaoJuridica && (
            <div className="ai-highlight">
              <span>Questão jurídica</span>
              <p>{citado(analise.questaoJuridica)}</p>
            </div>
          )}
          {analise.entendimentoPredominante && (
            <div className="ai-highlight strong">
              <span>Entendimento predominante</span>
              <p>{citado(analise.entendimentoPredominante)}</p>
            </div>
          )}
        </div>
      )}

      {analise.pontosPrincipais.length > 0 && (
        <>
          <h4>Pontos principais</h4>
          <ul className="ai-list">
            {analise.pontosPrincipais.map((ponto, i) => <li key={i}>{citado(ponto)}</li>)}
          </ul>
        </>
      )}

      {analise.fundamentosLegais.length > 0 && (
        <>
          <h4>Fundamentos legais citados</h4>
          <ul className="ai-chips">
            {analise.fundamentosLegais.map((fundamento, i) => <li key={i}>{citado(fundamento)}</li>)}
          </ul>
        </>
      )}

      {analise.divergencias.length > 0 && (
        <>
          <h4>Divergências</h4>
          <ul className="ai-list">
            {analise.divergencias.map((divergencia, i) => <li key={i}>{citado(divergencia)}</li>)}
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
                {doc.motivo && <p>{citado(doc.motivo)}</p>}
              </li>
            ))}
          </ol>
        </>
      )}

      {analise.fontes && <FontesAnalise fontes={analise.fontes} onAbrirDocumento={onAbrirDocumento} />}

      <ResultadosUtilizados analise={analise} />

      {analise.limitacoes.length > 0 && (
        <>
          <h4>Limitações</h4>
          <ul className="ai-list muted">
            {analise.limitacoes.map((limitacao, i) => <li key={i}>{citado(limitacao)}</li>)}
          </ul>
        </>
      )}

      <p className="ai-meta">
        Gerada com auxílio de IA pelo modelo {analise.modelo}
        {Number.isNaN(geradaEm.getTime()) ? '' : ` em ${horario.format(geradaEm)}`}
        {' · '}Os números entre colchetes, como [1], indicam a posição do documento na lista de resultados e levam à fonte correspondente.
      </p>
    </div>
  )
}
