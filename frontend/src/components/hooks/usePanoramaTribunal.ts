import { useDashboard, type RecorteDashboard } from './useDashboard'

export function usePanoramaTribunal(tribunal: string | null) {
  const recorte: RecorteDashboard | null = tribunal
    ? {
        termo: '',
        fontes: [],
        tribunal,
        dataDe: '',
        dataAte: '',
        resultado: null,
        orgaoJulgador: null,
      }
    : null

  return useDashboard(recorte)
}