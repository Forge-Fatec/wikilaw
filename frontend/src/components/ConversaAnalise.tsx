import { useEffect, useRef, useState, type FormEvent } from 'react'
import {
  MAX_CARACTERES_PERGUNTA,
  perguntarSobreAnalise,
  type DocumentoCitado,
  type PesquisaAtiva,
  type ReferenciaDocumento,
} from '../analiseIa'

interface Mensagem {
  pergunta: string
  resposta: string
  citados: DocumentoCitado[]
}

interface Props {
  termo: string
  pesquisa: PesquisaAtiva
  resultados: ReferenciaDocumento[]
  onAbrirDocumento: (referencia: ReferenciaDocumento) => void
}

/**
 * Perguntas complementares sobre a análise. A conversa fica só na tela: gerar a análise de novo
 * ou mudar a pesquisa começa outra.
 */
export function ConversaAnalise({ termo, pesquisa, resultados, onAbrirDocumento }: Props) {
  const [mensagens, setMensagens] = useState<Mensagem[]>([])
  const [pergunta, setPergunta] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [historicoResumido, setHistoricoResumido] = useState(false)
  const requisicao = useRef<AbortController | null>(null)

  useEffect(() => () => requisicao.current?.abort(), [])

  async function perguntar(event: FormEvent) {
    event.preventDefault()
    const texto = pergunta.trim()
    if (!texto || enviando) return
    requisicao.current?.abort()
    const controle = new AbortController()
    requisicao.current = controle
    setEnviando(true)
    setErro(null)
    try {
      const historico = mensagens.map(({ pergunta, resposta }) => ({ pergunta, resposta }))
      const resposta = await perguntarSobreAnalise(termo, pesquisa, resultados, texto, historico, controle.signal)
      if (controle.signal.aborted) return
      setMensagens((atuais) => [...atuais, { pergunta: texto, resposta: resposta.resposta, citados: resposta.documentosCitados }])
      setHistoricoResumido(resposta.historicoResumido)
      setPergunta('')
    } catch (error) {
      if (controle.signal.aborted) return
      // A conversa e a pergunta digitada ficam, para tentar de novo.
      setErro(error instanceof Error ? error.message : 'Não foi possível responder à pergunta. Tente novamente.')
    } finally {
      if (!controle.signal.aborted) setEnviando(false)
    }
  }

  function novaConversa() {
    requisicao.current?.abort()
    setMensagens([])
    setErro(null)
    setEnviando(false)
    setHistoricoResumido(false)
  }

  return (
    <section className="ai-chat" aria-labelledby="ai-chat-title">
      <div className="ai-chat-header">
        <h4 id="ai-chat-title">Perguntas sobre a análise</h4>
        {mensagens.length > 0 && (
          <button type="button" className="ai-chat-reset" onClick={novaConversa}>Nova conversa</button>
        )}
      </div>
      <p className="ai-chat-hint">
        Pergunte sobre os documentos analisados, por exemplo: “esse entendimento vale para aposentados?”.
      </p>

      {mensagens.length > 0 && (
        <ol className="ai-chat-messages" aria-label="Histórico de perguntas e respostas">
          {mensagens.map((mensagem, index) => (
            <li key={index} className="ai-chat-exchange">
              <p className="ai-chat-question"><span>Você</span>{mensagem.pergunta}</p>
              <div className="ai-chat-answer">
                <span>IA</span>
                <p>{mensagem.resposta}</p>
                {mensagem.citados.length > 0 && (
                  <ul className="ai-chat-citations" aria-label="Documentos citados">
                    {mensagem.citados.map((doc) => (
                      <li key={`${doc.categoria}-${doc.id}`}>
                        <button type="button" className="ai-document-link" onClick={() => onAbrirDocumento(doc)}>
                          [{doc.posicao}] {doc.titulo ?? 'Documento sem título'}
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </li>
          ))}
        </ol>
      )}

      {historicoResumido && (
        <p className="ai-chat-note">Para caber no limite da IA, só as perguntas mais recentes foram consideradas.</p>
      )}
      {erro && <p className="ai-chat-error" role="alert">{erro}</p>}

      <form className="ai-chat-form" onSubmit={(event) => void perguntar(event)}>
        <label htmlFor="ai-chat-input" className="sr-only">Pergunta sobre a análise</label>
        <input
          id="ai-chat-input"
          type="text"
          value={pergunta}
          maxLength={MAX_CARACTERES_PERGUNTA}
          onChange={(event) => setPergunta(event.target.value)}
          placeholder="Faça uma pergunta sobre a análise…"
          disabled={enviando}
        />
        <button type="submit" className="ai-analysis-btn" disabled={enviando || !pergunta.trim()} aria-busy={enviando}>
          {enviando ? 'Respondendo…' : 'Perguntar'}
        </button>
      </form>
    </section>
  )
}
