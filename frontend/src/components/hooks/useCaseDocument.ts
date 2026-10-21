import { useCallback, useEffect, useRef, useState } from 'react'
import { MAX_DOCUMENT_BYTES, uploadCaseDocument, type CaseDocument } from '../../caseDocument'

export function useCaseDocument() {
  const [documento, setDocumento] = useState<CaseDocument | null>(null)
  const [processando, setProcessando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const uploadRef = useRef<AbortController | null>(null)

  useEffect(() => () => uploadRef.current?.abort(), [])

  const cancelar = useCallback(() => {
    uploadRef.current?.abort()
    uploadRef.current = null
    setProcessando(false)
    setErro(null)
  }, [])

  const enviar = useCallback(async (file: File) => {
    // Selecting another file invalidates the previous in-flight upload.
    cancelar()
    if (!/\.(pdf|docx)$/i.test(file.name)) {
      setErro('Formato não suportado. Envie um arquivo PDF ou DOCX.')
      return
    }
    if (!file.size) { setErro('O arquivo está vazio. Selecione outro documento.'); return }
    if (file.size > MAX_DOCUMENT_BYTES) { setErro('O arquivo excede o limite de 10 MB.'); return }
    const upload = new AbortController()
    uploadRef.current = upload
    setProcessando(true)
    try {
      const processed = await uploadCaseDocument(file, upload.signal)
      if (!upload.signal.aborted) setDocumento(processed)
    } catch (error) {
      if (!upload.signal.aborted) setErro(error instanceof Error && !(error instanceof TypeError)
        ? error.message : 'Não foi possível enviar o documento. Verifique a conexão e tente novamente.')
    } finally {
      if (uploadRef.current === upload) { uploadRef.current = null; setProcessando(false) }
    }
  }, [cancelar])

  const remover = useCallback(() => { cancelar(); setDocumento(null) }, [cancelar])
  const editar = useCallback((texto: string) => {
    setDocumento((current) => current ? { ...current, texto, caracteres: texto.length } : null)
  }, [])

  return { documento, processando, erro, enviar, remover, cancelar, editar }
}
