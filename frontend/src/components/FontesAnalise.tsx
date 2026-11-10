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
              </li>
            ))}
          </ol>
        )}
    </section>
  )
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
