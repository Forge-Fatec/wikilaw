export const RETICENCIAS = '…'

export type ItemPaginacao = number | typeof RETICENCIAS

const PAGINAS_NAS_PONTAS = 2
const PAGINAS_VIZINHAS = 2

// Primeiras páginas, vizinhas da atual e últimas páginas, com reticências nos
// saltos. Um salto de uma página só mostra o número: as reticências ocupariam
// o mesmo espaço sem ajudar.
export function paginasVisiveis(atual: number, total: number): ItemPaginacao[] {
  const paginas = new Set<number>()
  const adicionarIntervalo = (inicio: number, fim: number) => {
    for (let pagina = Math.max(1, inicio); pagina <= Math.min(total, fim); pagina += 1) {
      paginas.add(pagina)
    }
  }

  adicionarIntervalo(1, PAGINAS_NAS_PONTAS)
  adicionarIntervalo(atual - PAGINAS_VIZINHAS, atual + PAGINAS_VIZINHAS)
  adicionarIntervalo(total - PAGINAS_NAS_PONTAS + 1, total)

  const itens: ItemPaginacao[] = []
  let anterior = 0
  for (const pagina of [...paginas].sort((a, b) => a - b)) {
    if (pagina - anterior === 2) itens.push(anterior + 1)
    else if (pagina - anterior > 2) itens.push(RETICENCIAS)
    itens.push(pagina)
    anterior = pagina
  }
  return itens
}
