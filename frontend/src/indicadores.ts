export type ResultadoDecisao = 'provido' | 'parcial' | 'desprovido' | 'indefinido'

export interface Indicadores {
  totalDecisoes: number
  classificadas: number
  favoraveis: number
  // Percentual de 0 a 100; null quando nenhuma decisão tem resultado identificável.
  aderencia: number | null
}

interface DecisaoAnalisavel {
  type: string
  decisao: string | null
}

// A ordem importa: "desprovido" e "improcedente" contêm "provido" e "procedente".
const PADROES_DESPROVIDO =
  /\b(desprovid|improvid|nao provid|neg[\w-]* (o )?provimento|improceden)/
const PADROES_PARCIAL =
  /\b(parcial(mente)? provid|provid\w* em parte|parcial provimento|provimento parcial|parcialmente procedente|procedente em parte)/
const PADROES_PROVIDO =
  /\b(provid|(deu|deram|dar|dou|dado|da-se) provimento|procedente)/

function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
}

export function classificarResultado(decisao: string | null): ResultadoDecisao {
  if (!decisao?.trim()) return 'indefinido'

  const texto = normalizar(decisao)
  if (PADROES_DESPROVIDO.test(texto)) return 'desprovido'
  if (PADROES_PARCIAL.test(texto)) return 'parcial'
  if (PADROES_PROVIDO.test(texto)) return 'provido'
  return 'indefinido'
}

// Só jurisprudência entra na conta: em precedentes o campo "decisao" traz a tese.
export function calcularIndicadores(documentos: DecisaoAnalisavel[]): Indicadores {
  const decisoes = documentos.filter((doc) => doc.type === 'jurisprudencia')
  const resultados = decisoes.map((doc) => classificarResultado(doc.decisao))
  const classificadas = resultados.filter((r) => r !== 'indefinido').length
  const favoraveis = resultados.filter(
    (r) => r === 'provido' || r === 'parcial',
  ).length

  return {
    totalDecisoes: decisoes.length,
    classificadas,
    favoraveis,
    aderencia: classificadas
      ? Math.round((favoraveis / classificadas) * 100)
      : null,
  }
}
