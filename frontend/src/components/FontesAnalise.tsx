import { idFonte, type FonteAnalise, type ReferenciaDocumento } from '../analiseIa'

interface Props {
  fontes: FonteAnalise[]
  onAbrirDocumento: (referencia: ReferenciaDocumento) => void
}

/**
 * Fontes que fundamentam a análise: só documentos que a IA citou e o back conferiu.
 * Cada item mostra a identificação na fonte original e as seções que ele sustenta.
 */
export function FontesAnalise({ fontes, onAbrirDocumento }: Props) {
  return (
    <section className="ai-sources" aria-labelledby="ai-sources-title">
      <h4 id="ai-sources-title">Fontes utilizadas</h4>
      {fontes.length === 0
        ? (
          <p className="ai-sources-empty">
            Nenhuma informação desta análise foi atribuída a um documento verificável da pesquisa.
          </p>
        )
        : (
          <ol className="ai-sources-list">
            {fontes.map((fonte) => (
              <li key={`${fonte.categoria}-${fonte.id}`} id={idFonte(fonte.numero)} tabIndex={-1}>
                <span className="ai-source-number" aria-label={`Fonte ${fonte.numero}`}>[{fonte.numero}]</span>
                <div className="ai-source-body">
                  <button type="button" className="ai-document-link" onClick={() => onAbrirDocumento(fonte)}>
                    {fonte.titulo ?? 'Documento sem título'}
                  </button>
                  <p className="ai-source-meta">{identificacao(fonte)}</p>
                  {fonte.secoes.length > 0 && (
                    <p className="ai-source-sections">Sustenta: {fonte.secoes.join(', ')}</p>
                  )}
                </div>
                <LinkOriginal fonte={fonte} />
              </li>
            ))}
          </ol>
        )}
    </section>
  )
}

/** Abre o documento ou registro jurídico original numa nova aba; sem endereço válido, avisa que não há link. */
function LinkOriginal({ fonte }: { fonte: FonteAnalise }) {
  const url = urlSegura(fonte.url)
  if (!url) {
    return (
      <span className="ai-source-link indisponivel" title="Esta fonte não informou um link para o documento original.">
        Original indisponível
      </span>
    )
  }
  return (
    <a className="ai-source-link" href={url} target="_blank" rel="noopener noreferrer">
      Abrir original ↗
      <span className="sr-only"> de {fonte.titulo ?? `fonte ${fonte.numero}`} (abre em nova aba)</span>
    </a>
  )
}

// O back já filtra, mas a tela confere de novo: nunca abre esquemas como javascript:.
function urlSegura(url: string | null) {
  if (!url?.trim()) return null
  try {
    const { protocol } = new URL(url.trim())
    return protocol === 'http:' || protocol === 'https:' ? url.trim() : null
  } catch {
    return null
  }
}

const CATEGORIAS: Record<string, string> = {
  decisoes: 'Decisão judicial',
  precedentes: 'Precedente',
  doutrina: 'Doutrina',
}

function identificacao(fonte: FonteAnalise) {
  return [
    CATEGORIAS[fonte.categoria] ?? fonte.categoria,
    fonte.fonte,
    fonte.tribunal && fonte.tribunal !== fonte.fonte ? fonte.tribunal : null,
    fonte.numeroProcessoOuTema,
    fonte.identificadorExterno && fonte.identificadorExterno !== fonte.numeroProcessoOuTema
      ? `ID na fonte: ${fonte.identificadorExterno}`
      : null,
  ].filter(Boolean).join(' · ')
}
