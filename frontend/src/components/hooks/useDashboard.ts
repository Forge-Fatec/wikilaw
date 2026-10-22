import { useEffect, useState } from 'react'
import { API_BASE } from '../../api'
import { apiFetch } from '../../auth'

export interface DistribuicaoItem {
  categoria: string
  quantidade: number
}

export interface Dashboard {
  indicadores: {
    totalDecisoes: number
    classificadas: number
    favoraveis: number
    // Percentual de 0 a 100; null quando nenhuma decisão tem resultado identificável.
    aderencia: number | null
  }
  distribuicaoMerito: DistribuicaoItem[]
  distribuicaoCamaraTurma: DistribuicaoItem[]
  distribuicaoTribunal: DistribuicaoItem[]
  evolucaoAnual: DistribuicaoItem[]
}

export interface RecorteDashboard {
  termo: string
  fonte: string | null
  tribunal: string | null
  dataDe: string
  dataAte: string
  // Categorias selecionadas nos gráficos (rótulo da barra).
  resultado: string | null
  orgaoJulgador: string | null
}

// Mesmos rótulos que o back usa nas distribuições (DashboardService).
export const NAO_INFORMADO = 'Não informado'
export const OUTROS = 'Outros'

export const DASHBOARD_VAZIO: Dashboard = {
  indicadores: { totalDecisoes: 0, classificadas: 0, favoraveis: 0, aderencia: null },
  distribuicaoMerito: [],
  distribuicaoCamaraTurma: [],
  distribuicaoTribunal: [],
  evolucaoAnual: [],
}

const ERRO_DASHBOARD = 'Não foi possível carregar o dashboard da pesquisa.'

function urlDoRecorte(recorte: RecorteDashboard): string {
  const params = new URLSearchParams()
  if (recorte.termo.trim()) params.set('termo', recorte.termo.trim())
  if (recorte.fonte) params.set('fonte', recorte.fonte)
  if (recorte.tribunal) params.set('tribunal', recorte.tribunal)
  if (recorte.dataDe) params.set('dataDe', recorte.dataDe)
  if (recorte.dataAte) params.set('dataAte', recorte.dataAte)
  if (recorte.resultado) params.set('resultado', recorte.resultado)
  if (recorte.orgaoJulgador) params.set('orgaoJulgador', recorte.orgaoJulgador)
  return `${API_BASE}/api/dashboard?${params}`
}

interface Resposta {
  url: string
  dados: Dashboard | null
  erro: string | null
}

// Recorte null significa que nenhuma decisão entra na pesquisa (jurisprudência
// desmarcada), então não há o que consultar.
export function useDashboard(recorte: RecorteDashboard | null) {
  const url = recorte ? urlDoRecorte(recorte) : null
  const [resposta, setResposta] = useState<Resposta | null>(null)

  useEffect(() => {
    if (!url) return

    // Descarta respostas de um recorte que já mudou (filtro trocado no meio da consulta).
    let ativo = true
    apiFetch(url)
      .then((res) => {
        if (!res.ok) throw new Error(`Erro ao carregar o dashboard (status ${res.status})`)
        return res.json() as Promise<Dashboard>
      })
      .then((dados) => {
        if (ativo) setResposta({ url, dados, erro: null })
      })
      .catch(() => {
        if (ativo) setResposta({ url, dados: null, erro: ERRO_DASHBOARD })
      })

    return () => {
      ativo = false
    }
  }, [url])

  if (!url) return { dados: DASHBOARD_VAZIO, carregando: false, erro: null }

  // Enquanto a resposta do recorte atual não chega, não reaproveita a anterior.
  const atual = resposta?.url === url ? resposta : null
  return {
    dados: atual?.dados ?? null,
    carregando: atual === null,
    erro: atual?.erro ?? null,
  }
}
