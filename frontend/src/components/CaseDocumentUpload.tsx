import { useId, useRef } from 'react'
import type { useCaseDocument } from './hooks/useCaseDocument'

export function CaseDocumentUpload({ context, compact = false, onInspect }: {
  context: ReturnType<typeof useCaseDocument>; compact?: boolean; onInspect?: () => void
}) {
  const input = useRef<HTMLInputElement>(null)
  const id = useId()
  const { documento, processando, erro, enviar, remover, cancelar, editar } = context

  return (
    <div className="case-document">
      <div className="case-document-actions">
        <input ref={input} id={id} type="file" hidden accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          aria-label="Selecionar documento do caso" onChange={(event) => {
            const file = event.target.files?.[0]
            event.target.value = ''
            if (file) void enviar(file)
          }} />
        <button type="button" className="attach-document-btn" title={documento?.nome} onClick={() => input.current?.click()}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true">
            <path d="m8 13 7-7a3 3 0 0 1 4 4L9 20a5 5 0 0 1-7-7L13 2" />
          </svg>
          <span>{documento ? compact ? documento.nome : 'Substituir documento' : 'Anexar documento'}</span>
        </button>
        <span className="case-document-help">PDF ou DOCX · até 10 MB</span>
        {compact && documento && <>
          <button type="button" className="document-icon-btn" onClick={onInspect} aria-label="Revisar texto do documento" title="Revisar texto">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7Z" /><circle cx="12" cy="12" r="3" /></svg>
          </button>
          <button type="button" className="document-icon-btn" onClick={remover} aria-label="Remover documento do caso" title="Remover documento">×</button>
        </>}
      </div>
      {processando && <div className="case-document-progress" role="status">
        Lendo documento… <button type="button" onClick={cancelar}>Cancelar envio</button>
      </div>}
      {erro && <p className="case-document-error" role="alert">{erro}</p>}
      {documento && !compact && <div className="case-document-card">
        <div className="case-document-heading">
          <div>
            <strong>{documento.nome}</strong>
            <small>{documento.formato}{documento.paginas ? ` · ${documento.paginas} ${documento.paginas === 1 ? 'página' : 'páginas'}` : ''} · {documento.caracteres.toLocaleString('pt-BR')} caracteres</small>
          </div>
          <button type="button" className="remove-document-btn" onClick={remover} aria-label="Remover documento do caso">Remover</button>
        </div>
        <p>O texto deste documento será usado como contexto ao pesquisar.</p>
        <details className="case-document-preview">
          <summary>Conferir texto extraído</summary>
          <label htmlFor={`${id}-text`}>Contexto do documento</label>
          <textarea id={`${id}-text`} value={documento.texto} maxLength={100000} rows={6} disabled={processando}
            onChange={(event) => editar(event.target.value)} />
          <small>Você pode revisar o texto. O arquivo não é adicionado ao acervo público.</small>
        </details>
      </div>}
    </div>
  )
}
