import { useCallback, useEffect, useRef, useState } from 'react'
import {
  buscarMarco,
  buscarNovidades,
  carregarAcompanhamentos,
  mesmaPesquisa,
  salvarAcompanhamentos,
  type NovaPesquisaAcompanhada,
  type PesquisaAcompanhada,
} from '../../acompanhamentos'

// Com a tela aberta, verifica as pesquisas acompanhadas de tempos em tempos. O vetor da
// descrição fica em cache no back, então repetir a verificação não gasta outra chamada à IA.
export const INTERVALO_VERIFICACAO_MS = 30 * 60 * 1000

export function useAcompanhamentos(login: string) {
  const [lista, setLista] = useState<PesquisaAcompanhada[]>(() => carregarAcompanhamentos(login))
  const [verificando, setVerificando] = useState(false)
  const listaRef = useRef(lista)

  useEffect(() => {
    listaRef.current = lista
    salvarAcompanhamentos(login, lista)
  }, [login, lista])

  // Uma pesquisa por vez, para não estourar o limite por minuto da IA.
  const verificar = useCallback(async () => {
    const pesquisas = listaRef.current
    if (!pesquisas.length) return
    setVerificando(true)
    const atualizadas = new Map<string, Partial<PesquisaAcompanhada>>()
    for (const pesquisa of pesquisas) {
      try {
        atualizadas.set(pesquisa.id, {
          novidades: await buscarNovidades(pesquisa),
          verificadaEm: new Date().toISOString(),
          erro: null,
        })
      } catch (falha) {
        atualizadas.set(pesquisa.id, { erro: falha instanceof Error ? falha.message : 'Falha na verificação.' })
      }
    }
    setLista((atual) => atual.map((pesquisa) => ({ ...pesquisa, ...atualizadas.get(pesquisa.id) })))
    setVerificando(false)
  }, [])

  useEffect(() => {
    void verificar()
    const intervalo = window.setInterval(() => void verificar(), INTERVALO_VERIFICACAO_MS)
    return () => window.clearInterval(intervalo)
  }, [verificar])

  // O que já existe ao começar a acompanhar vira o marco: só o que entrar depois é novidade.
  const acompanhar = useCallback(async (nova: NovaPesquisaAcompanhada) => {
    if (listaRef.current.some((pesquisa) => mesmaPesquisa(pesquisa, nova))) return
    const marco = await buscarMarco()
    setLista((atual) => [{
      ...nova,
      id: crypto.randomUUID(),
      marco,
      criadaEm: new Date().toISOString(),
      verificadaEm: null,
      novidades: [],
      erro: null,
    }, ...atual])
  }, [])

  const deixarDeAcompanhar = useCallback((id: string) => {
    setLista((atual) => atual.filter((pesquisa) => pesquisa.id !== id))
  }, [])

  // Avança o marco: as novidades atuais deixam de ser novas.
  const marcarComoVistas = useCallback(async (id: string) => {
    const marco = await buscarMarco()
    setLista((atual) => atual.map((pesquisa) => (pesquisa.id === id ? { ...pesquisa, marco, novidades: [] } : pesquisa)))
  }, [])

  const estaAcompanhando = useCallback(
    (pesquisa: NovaPesquisaAcompanhada) => lista.some((acompanhada) => mesmaPesquisa(acompanhada, pesquisa)),
    [lista],
  )

  const totalNovidades = lista.reduce((total, pesquisa) => total + pesquisa.novidades.length, 0)

  return { lista, verificando, verificar, acompanhar, deixarDeAcompanhar, marcarComoVistas, estaAcompanhando, totalNovidades }
}
