const DURACAO_MS = 900

// Interação do usuário que interrompe a animação, para ela não brigar com ele.
const EVENTOS_DO_USUARIO = ['wheel', 'touchstart', 'mousedown', 'keydown'] as const

// Começa e termina devagar (ease-in-out cúbico).
function suavizar(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2
}

// Rola a janela até destino(), recalculado a cada quadro porque a barra fixa
// muda de altura quando a busca se encaixa nela. Com movimento reduzido no
// sistema, vai direto. Devolve a função que cancela a animação.
export function rolarSuavementeAte(destino: () => number): () => void {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    window.scrollTo(0, destino())
    return () => {}
  }

  const inicio = window.scrollY
  const comeco = performance.now()
  let quadro = 0

  const cancelar = () => {
    cancelAnimationFrame(quadro)
    EVENTOS_DO_USUARIO.forEach((evento) => window.removeEventListener(evento, cancelar))
  }
  const passo = (agora: number) => {
    const progresso = Math.min(1, (agora - comeco) / DURACAO_MS)
    window.scrollTo(0, inicio + (destino() - inicio) * suavizar(progresso))
    if (progresso < 1) quadro = requestAnimationFrame(passo)
    else cancelar()
  }

  EVENTOS_DO_USUARIO.forEach((evento) =>
    window.addEventListener(evento, cancelar, { passive: true }),
  )
  quadro = requestAnimationFrame(passo)
  return cancelar
}
