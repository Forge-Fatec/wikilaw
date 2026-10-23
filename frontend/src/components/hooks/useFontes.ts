import { useEffect, useState } from 'react'
import { API_BASE } from '../../api'

export interface Fonte {
  sigla: string
  nome: string
}

interface EstadoFontes {
  fontes: Fonte[]
  carregando: boolean
  erro: string | null
}

const ERRO_FONTES =
  'Não foi possível carregar as fontes. A pesquisa vai considerar todas elas.'

// Fontes que podem filtrar a pesquisa (GET /api/fontes), carregadas uma vez.
export function useFontes(): EstadoFontes {
  const [estado, setEstado] = useState<EstadoFontes>({
    fontes: [],
    carregando: true,
    erro: null,
  })

  useEffect(() => {
    let ativo = true
    fetch(`${API_BASE}/api/fontes`)
      .then((res) => {
        if (!res.ok) throw new Error(`Erro ao carregar as fontes (status ${res.status})`)
        return res.json() as Promise<Fonte[]>
      })
      .then((fontes) => {
        if (ativo) setEstado({ fontes, carregando: false, erro: null })
      })
      .catch(() => {
        if (ativo) setEstado({ fontes: [], carregando: false, erro: ERRO_FONTES })
      })

    return () => {
      ativo = false
    }
  }, [])

  return estado
}
