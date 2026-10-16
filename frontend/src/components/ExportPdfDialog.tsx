import { useEffect, useRef, useState, type RefObject } from 'react'

interface Props {
  totalResults: number
  pageSize: number
  pending: boolean
  error: string | null
  returnFocusTo: RefObject<HTMLButtonElement | null>
  onClose: () => void
  onExport: (from: number, to: number) => void
}

export function ExportPdfDialog({ totalResults, pageSize, pending, error, returnFocusTo, onClose, onExport }: Props) {
  const totalPages = Math.ceil(totalResults / pageSize)
  const [mode, setMode] = useState<'all' | 'range'>('all')
  const [from, setFrom] = useState('1')
  const [to, setTo] = useState(String(totalPages))
  const dialog = useRef<HTMLElement>(null)
  const firstOption = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const previousFocus = returnFocusTo.current ?? document.activeElement
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    firstOption.current?.focus()
    return () => {
      document.body.style.overflow = previousOverflow
      if (previousFocus instanceof HTMLElement) previousFocus.focus()
    }
  }, [returnFocusTo])

  useEffect(() => { if (pending) dialog.current?.focus() }, [pending])

  const start = mode === 'all' ? 1 : Number(from)
  const end = mode === 'all' ? totalPages : Number(to)
  let validation: string | null = null
  if (!Number.isInteger(start) || !Number.isInteger(end) || start < 1 || end < 1) {
    validation = 'Informe páginas inteiras a partir de 1.'
  } else if (start > totalPages || end > totalPages) {
    validation = `A pesquisa tem ${totalPages} ${totalPages === 1 ? 'página' : 'páginas'}. Escolha um intervalo dentro desse limite.`
  } else if (start > end) {
    validation = 'A página inicial deve ser menor ou igual à página final.'
  }
  const count = validation ? 0 : Math.min(end * pageSize, totalResults) - (start - 1) * pageSize
  if (!validation && count > 5000) validation = 'Selecione um intervalo com até 5.000 documentos por PDF.'

  return (
    <div className="modal-overlay" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !pending) onClose()
    }}>
      <section
        className="export-modal"
        ref={dialog}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby="export-title"
        aria-describedby="export-description"
        aria-busy={pending}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault()
            if (!pending) onClose()
          }
          if (event.key === 'Tab') {
            const elements = Array.from(dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled)') ?? [])
            const first = elements[0]
            const last = elements[elements.length - 1]
            if (!elements.length) { event.preventDefault(); return }
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
          }
        }}
      >
        <button type="button" className="modal-close" aria-label="Fechar exportação" disabled={pending} onClick={onClose}>×</button>
        <span className="export-eyebrow">RELATÓRIO DA PESQUISA</span>
        <h2 id="export-title">Exportar resultados em PDF</h2>
        <p id="export-description">
          Escolha quais páginas dos resultados deseja incluir. Seus filtros e a ordenação serão mantidos.
        </p>
        <form noValidate onSubmit={(event) => { event.preventDefault(); if (!validation && !pending) onExport(start, end) }}>
          <fieldset className="export-options" disabled={pending}>
            <legend>Páginas para exportar</legend>
            <label className="export-option">
              <input ref={firstOption} type="radio" name="export-pages" value="all" checked={mode === 'all'} onChange={() => setMode('all')} />
              <span>Todas as páginas<small>{totalResults} {totalResults === 1 ? 'documento' : 'documentos'} em {totalPages} {totalPages === 1 ? 'página' : 'páginas'}</small></span>
            </label>
            <label className="export-option">
              <input type="radio" name="export-pages" value="range" checked={mode === 'range'} onChange={() => setMode('range')} />
              <span>Selecionar intervalo</span>
            </label>
            {mode === 'range' && (
              <div className="export-page-range">
                <label htmlFor="export-from">Da página
                  <input id="export-from" type="number" min={1} max={totalPages} step={1} value={from}
                    aria-invalid={Boolean(validation)} aria-describedby={validation ? 'export-validation' : 'export-page-help'}
                    onChange={(event) => setFrom(event.target.value)} />
                </label>
                <label htmlFor="export-to">Até a página
                  <input id="export-to" type="number" min={1} max={totalPages} step={1} value={to}
                    aria-invalid={Boolean(validation)} aria-describedby={validation ? 'export-validation' : 'export-page-help'}
                    onChange={(event) => setTo(event.target.value)} />
                </label>
              </div>
            )}
          </fieldset>
          <p className="export-page-help" id="export-page-help">Cada página da pesquisa tem até {pageSize} documentos. A paginação do PDF pode ser diferente.</p>
          {!validation && <p className="export-selection" aria-live="polite">{count} {count === 1 ? 'documento será incluído' : 'documentos serão incluídos'} no PDF.</p>}
          {validation && <p id="export-validation" className="export-message export-error" role="alert">{validation}</p>}
          {error && !validation && <p className="export-message export-error" role="alert">{error}</p>}
          {pending && <p className="export-progress" role="status">Gerando seu relatório. O download começará automaticamente.</p>}
          <div className="export-modal-actions">
            <button type="button" className="export-cancel-btn" disabled={pending} onClick={onClose}>Cancelar</button>
            <button type="submit" className="export-pdf-btn" disabled={pending || Boolean(validation)}>{pending ? 'Gerando PDF...' : 'Gerar e baixar PDF'}</button>
          </div>
        </form>
      </section>
    </div>
  )
}
