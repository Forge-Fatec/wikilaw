import { Fragment, type MouseEvent, type ReactNode } from 'react'
import { idFonte } from '../analiseIa'

interface Props {
  texto: string
  /** Números das fontes verificadas; só eles viram link. */
  fontes: ReadonlySet<number>
}

// O back devolve as citações já normalizadas como [1] ou [1, 3].
const CITACAO = /\[(\d+(?:, \d+)*)\]/g

/** Texto da análise com cada citação [n] ligada à fonte correspondente na lista de fontes. */
export function TextoComCitacoes({ texto, fontes }: Props) {
  const partes: ReactNode[] = []
  let inicio = 0
  for (const marcador of texto.matchAll(CITACAO)) {
    const numeros = marcador[1].split(', ').map(Number)
    partes.push(texto.slice(inicio, marcador.index))
    partes.push(
      <span key={marcador.index} className="ai-citation">
        [{numeros.map((numero, i) => (
          <Fragment key={numero}>
            {i > 0 && ', '}
            {fontes.has(numero)
              ? (
                <a href={`#${idFonte(numero)}`} onClick={(event) => irParaFonte(event, numero)}
                  aria-label={`Fonte ${numero}`}>
                  {numero}
                </a>
              )
              : numero}
          </Fragment>
        ))}]
      </span>,
    )
    inicio = marcador.index + marcador[0].length
  }
  partes.push(texto.slice(inicio))
  return <>{partes}</>
}

// Rola até a fonte sem trocar o hash da página.
function irParaFonte(event: MouseEvent<HTMLAnchorElement>, numero: number) {
  const alvo = document.getElementById(idFonte(numero))
  if (!alvo) return
  event.preventDefault()
  alvo.scrollIntoView({ behavior: 'smooth', block: 'center' })
  alvo.focus({ preventScroll: true })
}
