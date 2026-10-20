import { useState, useMemo, useEffect, useCallback, useRef } from 'react'
import './index.css'
import { API_BASE } from './api'
import { paginasVisiveis, RETICENCIAS } from './paginacao'
import { BarChartDistribuicao } from './components/charts/BarChartDistribuicao'
import { ExportPdfDialog } from './components/ExportPdfDialog'
import { PanoramaTribunal } from './components/PanoramaTribunal'
import {
  useDashboard,
  NAO_INFORMADO,
  OUTROS,
  type DistribuicaoItem,
} from './components/hooks/useDashboard'

type DocType = 'jurisprudencia' | 'precedente' | 'doutrina'
type Ordenacao = 'mais-recentes' | 'mais-antigos'
type EscopoPainel = 'pesquisa' | 'panorama'

// Categoria escolhida em cada gráfico do dashboard; null = sem filtro.
interface SelecaoGraficos {
  resultado: string | null
  orgaoJulgador: string | null
}

const SEM_SELECAO: SelecaoGraficos = { resultado: null, orgaoJulgador: null }

const TODOS_OS_TIPOS: Record<DocType, boolean> = {
  jurisprudencia: true,
  precedente: true,
  doutrina: true,
}

const ROTULO_POR_TIPO: Record<DocType, string> = {
  jurisprudencia: 'Jurisprudência',
  precedente: 'Precedente',
  doutrina: 'Doutrina',
}

// Onde está a busca: no hero, a caminho da barra fixa ou já dentro dela.
type FaseEncaixe = 'destaque' | 'transicao' | 'barra'

// Distância (px) do topo da tela em que a busca do hero começa a ir para a
// barra; o encaixe termina quando ela some pelo topo.
const INICIO_ENCAIXE = 160

interface FiltroAtivo {
  chave: string
  rotulo: string
  remover: () => void
}

const CATEGORIA_POR_TIPO: Record<DocType, string> = {
  jurisprudencia: 'decisoes',
  precedente: 'precedentes',
  doutrina: 'doutrina',
}

const BADGE_POR_TIPO: Record<DocType, string> = {
  jurisprudencia: 'JURISPRUDÊNCIA',
  precedente: 'PRECEDENTE',
  doutrina: 'DOUTRINA',
}

const ITENS_POR_PAGINA = 10
const ITENS_POR_PAGINA_API = 100
const TODAS_AS_FONTES = 'Todas as fontes'
const TODOS_OS_TRIBUNAIS = 'Todos os tribunais'

const ERRO_CARREGAMENTO =
  'Não foi possível carregar os resultados. Verifique se o back-end está rodando em ' +
  API_BASE +
  '.'

interface ApiSummary {
  id: number
  fonte: string
  categoria: string
  titulo: string
  tipoDocumento: string | null
  numeroProcessoOuTema: string | null
  autoresOuRelator: string | null
  resumoOuEmenta: string | null
  decisao: string | null
  // Rótulo do resultado do recurso, como no gráfico; só vem em decisões.
  resultado?: string | null
  tribunal: string | null
  orgaoJulgador: string | null
  dataJulgamento: string | null
  dataPublicacao: string | null
  dataOriginal: string | null
  urlOriginal: string | null
  idRegistroBruto: number | null
}

interface ApiResult {
  itens: ApiSummary[]
  pagina: number
  tamanho: number
  total: number
  totalPaginas: number
}

interface Documento {
  id: number
  type: DocType
  badge: string
  fonte: string
  title: string
  tipoDocumento: string | null
  numeroProcessoOuTema: string | null
  autoresOuRelator: string | null
  text: string
  decisao: string | null
  resultado: string | null
  tribunal: string | null
  orgaoJulgador: string | null
  dataJulgamento: string | null
  dataPublicacao: string | null
  dataOriginal: string | null
  urlOriginal: string | null
}

// Mesma normalização do back ao agrupar o gráfico de órgão julgador.
function orgaoDoGrafico(orgaoJulgador: string | null): string {
  return orgaoJulgador?.trim().replace(/\s+/g, ' ') || NAO_INFORMADO
}

function dataDoDocumento(documento: Documento): string | null {
  return documento.dataPublicacao ?? documento.dataJulgamento
}

function formatarData(data: string | null): string {
  if (!data) return 'Data não informada'
  const d = new Date(data)
  if (Number.isNaN(d.getTime())) return data
  return d.toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  })
}

// "2024-01-31" -> "31/01/2024", sem passar por Date para não sofrer com fuso.
function formatarDataCurta(data: string): string {
  const [ano, mes, dia] = data.split('-')
  return dia && mes && ano ? `${dia}/${mes}/${ano}` : data
}

// Só links http(s) são aceitos: evita abrir esquemas como javascript: vindos da fonte.
function urlDeAcesso(url: string | null): string | null {
  if (!url?.trim()) return null
  try {
    const { protocol } = new URL(url.trim())
    return protocol === 'http:' || protocol === 'https:' ? url.trim() : null
  } catch {
    return null
  }
}

function AcessoFonte({
  url,
  className,
}: {
  url: string | null
  className: string
}) {
  if (!url) {
    return (
      <button
        type="button"
        className={`${className} indisponivel`}
        disabled
        title="Esta fonte não informou um link para o documento original."
      >
        FONTE INDISPONÍVEL
      </button>
    )
  }

  return (
    <a href={url} target="_blank" rel="noopener noreferrer" className={className}>
      ACESSAR FONTE ORIGINAL ↗
      <span className="sr-only"> (abre em nova aba)</span>
    </a>
  )
}

function GraficoDashboard({
  titulo,
  dados,
  carregando,
  erro,
  horizontal = false,
  selecionada = null,
  onSelecionar,
  onLimparSelecao,
}: {
  titulo: string
  dados: DistribuicaoItem[] | undefined
  carregando: boolean
  erro: string | null
  horizontal?: boolean
  // Sem onSelecionar o gráfico é só de leitura: não filtra a lista.
  selecionada?: string | null
  onSelecionar?: (item: DistribuicaoItem) => void
  onLimparSelecao?: () => void
}) {
  const vazio = !dados?.some((item) => item.quantidade > 0)
  const exibeGrafico = !carregando && !erro && !vazio

  return (
    <div className="chart-container">
      <h3>{titulo}</h3>
      {/* Fica visível mesmo sem gráfico: o filtro continua valendo na lista. */}
      {selecionada ? (
        <button
          type="button"
          className="chart-selecao"
          aria-label={`Remover filtro ${selecionada}`}
          onClick={onLimparSelecao}
        >
          Filtrando: {selecionada}
          <span aria-hidden="true"> ×</span>
        </button>
      ) : (
        exibeGrafico && onSelecionar && (
          <p className="chart-hint">Clique em uma barra para filtrar os resultados.</p>
        )
      )}
      {carregando ? (
        <p className="chart-loading">Carregando gráfico...</p>
      ) : erro ? (
        <p className="chart-error">{erro}</p>
      ) : vazio ? (
        <p className="chart-loading">Nenhuma decisão na pesquisa atual.</p>
      ) : (
        <BarChartDistribuicao
          data={dados ?? []}
          horizontal={horizontal}
          selecionada={selecionada}
          onSelecionar={onSelecionar}
        />
      )}
    </div>
  )
}

function toDocumento(tipo: DocType, item: ApiSummary): Documento {
  return {
    id: item.id,
    type: tipo,
    badge: BADGE_POR_TIPO[tipo],
    fonte: item.fonte ?? 'Fonte não informada',
    title: item.titulo,
    tipoDocumento: item.tipoDocumento,
    numeroProcessoOuTema: item.numeroProcessoOuTema,
    autoresOuRelator: item.autoresOuRelator,
    text: item.resumoOuEmenta ?? 'Sem resumo disponível.',
    decisao: item.decisao,
    resultado: item.resultado ?? null,
    tribunal: item.tribunal,
    orgaoJulgador: item.orgaoJulgador,
    dataJulgamento: item.dataJulgamento,
    dataPublicacao: item.dataPublicacao,
    dataOriginal: item.dataOriginal,
    urlOriginal: urlDeAcesso(item.urlOriginal),
  }
}

async function carregarPagina(
  tipo: DocType,
  termo: string,
  pagina: number,
  signal: AbortSignal,
): Promise<ApiResult> {
  const categoria = CATEGORIA_POR_TIPO[tipo]
  const params = new URLSearchParams({
    pagina: String(pagina),
    tamanho: String(ITENS_POR_PAGINA_API),
  })
  if (termo.trim()) params.set('termo', termo.trim())

  const res = await fetch(`${API_BASE}/api/documentos/${categoria}?${params}`, { signal })
  if (!res.ok) {
    throw new Error(`Erro ao buscar ${categoria} (status ${res.status})`)
  }
  return res.json()
}

async function carregarCategoria(
  tipo: DocType,
  termo: string,
  signal: AbortSignal,
): Promise<Documento[]> {
  const primeira = await carregarPagina(tipo, termo, 0, signal)
  const documentos = primeira.itens.map((item) => toDocumento(tipo, item))

  for (let pagina = 1; pagina < primeira.totalPaginas; pagina += 1) {
    const data = await carregarPagina(tipo, termo, pagina, signal)
    documentos.push(...data.itens.map((item) => toDocumento(tipo, item)))
  }

  return documentos
}

// O signal interrompe a carga quando outra pesquisa a substitui: sem isso, a
// carga inicial (dezenas de milhares de documentos) seguiria ocupando a rede.
async function carregarDocumentos(
  termo: string,
  signal: AbortSignal,
): Promise<Documento[]> {
  const respostas = await Promise.allSettled(
    (Object.keys(CATEGORIA_POR_TIPO) as DocType[]).map((tipo) =>
      carregarCategoria(tipo, termo, signal),
    ),
  )

  const documentos = respostas.flatMap((resposta) =>
    resposta.status === 'fulfilled' ? resposta.value : [],
  )
  if (!documentos.length && respostas.some((resposta) => resposta.status === 'rejected')) {
    throw new Error('Nenhuma categoria pôde ser carregada')
  }

  return documentos
}

function App() {
  const [query, setQuery] = useState('')
  // The dashboard and PDF describe the last submitted search, not the current input.
  const [termoPesquisado, setTermoPesquisado] = useState('')
  const [exportando, setExportando] = useState(false)
  const [modalExportacao, setModalExportacao] = useState(false)
  const botaoExportar = useRef<HTMLButtonElement>(null)
  const [erroExportacao, setErroExportacao] = useState<string | null>(null)
  const [exportacaoConcluida, setExportacaoConcluida] = useState(false)
  const [erroValidacao, setErroValidacao] = useState<string | null>(null)
  const [filtros, setFiltros] = useState<Record<DocType, boolean>>(TODOS_OS_TIPOS)
  const [filtrosAbertos, setFiltrosAbertos] = useState(false)
  const faixaRef = useRef<HTMLDivElement>(null)
  const buscaDestaqueRef = useRef<HTMLDivElement>(null)
  const cargaRef = useRef<AbortController | null>(null)
  const [encaixe, setEncaixe] = useState<FaseEncaixe>('destaque')

  // A busca do hero "desce" para a barra fixa conforme a página rola e volta
  // ao subir. O progresso (0 a 1) vai para a variável CSS --encaixe, que
  // anima as duas buscas acompanhando a rolagem; o estado React só muda
  // nas fronteiras, para não renderizar a cada quadro.
  useEffect(() => {
    const raiz = document.documentElement
    let quadro = 0

    const atualizar = () => {
      quadro = 0
      const busca = buscaDestaqueRef.current
      const faixa = faixaRef.current
      if (!busca || !faixa) return

      const { bottom } = busca.getBoundingClientRect()
      const progresso = Math.min(1, Math.max(0, (INICIO_ENCAIXE - bottom) / INICIO_ENCAIXE))
      raiz.style.setProperty('--encaixe', progresso.toFixed(3))
      setEncaixe(progresso === 0 ? 'destaque' : progresso === 1 ? 'barra' : 'transicao')
      // Só pinta o fundo da faixa quando ela está presa: antes disso ela fica
      // sobre o hero e o fundo apareceria como uma tarja.
      faixa.toggleAttribute('data-presa', faixa.getBoundingClientRect().top <= 0)
    }
    const agendar = () => {
      if (!quadro) quadro = requestAnimationFrame(atualizar)
    }

    agendar()
    window.addEventListener('scroll', agendar, { passive: true })
    window.addEventListener('resize', agendar)
    return () => {
      cancelAnimationFrame(quadro)
      window.removeEventListener('scroll', agendar)
      window.removeEventListener('resize', agendar)
      raiz.style.removeProperty('--encaixe')
    }
  }, [])

  // O panorama (também fixo) precisa começar abaixo da faixa; a altura muda
  // quando o painel de filtros abre, a busca entra ou os chips quebram linha.
  useEffect(() => {
    const faixa = faixaRef.current
    if (!faixa) return

    const raiz = document.documentElement
    const observador = new ResizeObserver(() => {
      raiz.style.setProperty('--altura-barra', `${faixa.offsetHeight}px`)
    })
    observador.observe(faixa)
    return () => {
      observador.disconnect()
      raiz.style.removeProperty('--altura-barra')
    }
  }, [])
  const [tribunal, setTribunal] = useState(TODOS_OS_TRIBUNAIS)
  const [fonte, setFonte] = useState(TODAS_AS_FONTES)
  const [dataDe, setDataDe] = useState('')
  const [dataAte, setDataAte] = useState('')
  const [ordenacao, setOrdenacao] = useState<Ordenacao>('mais-recentes')
  const [selecao, setSelecao] = useState<SelecaoGraficos>(SEM_SELECAO)
  const [escopo, setEscopo] = useState<EscopoPainel>('pesquisa')

  const [documentos, setDocumentos] = useState<Documento[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)
  const [paginaAtual, setPaginaAtual] = useState(1)
  const [documentoSelecionado, setDocumentoSelecionado] =
    useState<Documento | null>(null)

  // O dashboard é calculado no back com o mesmo recorte da lista. Só
  // jurisprudência entra na conta, então sem ela não há o que consultar.
  const dashboard = useDashboard(
    filtros.jurisprudencia
      ? {
          termo: termoPesquisado,
          fonte: fonte === TODAS_AS_FONTES ? null : fonte,
          tribunal: tribunal === TODOS_OS_TRIBUNAIS ? null : tribunal,
          dataDe,
          dataAte,
          ...selecao,
        }
      : null,
  )
  const indicadores = dashboard.dados?.indicadores

  const buscar = useCallback(async () => {
    if (!query.trim()) {
      setErroValidacao('Informe a descrição do caso para realizar a pesquisa.')
      return
    }

    // Pesquisar não espera a carga em andamento (a inicial demora): ela é
    // cancelada e só o resultado desta pesquisa chega à tela.
    cargaRef.current?.abort()
    const carga = new AbortController()
    cargaRef.current = carga

    setErroValidacao(null)
    setErroExportacao(null)
    setExportacaoConcluida(false)
    setTermoPesquisado(query.trim())
    setCarregando(true)
    setErro(null)
    setPaginaAtual(1)
    setDocumentoSelecionado(null)
    try {
      const resultados = await carregarDocumentos(query, carga.signal)
      if (!carga.signal.aborted) setDocumentos(resultados)
    } catch {
      if (!carga.signal.aborted) {
        setErro(ERRO_CARREGAMENTO)
        setDocumentos([])
      }
    } finally {
      if (!carga.signal.aborted) setCarregando(false)
    }
  }, [query])

  // Carrega os resultados iniciais (sem termo) assim que a página abre.
  useEffect(() => {
    const carga = new AbortController()
    cargaRef.current = carga

    carregarDocumentos('', carga.signal)
      .then((resultados) => {
        if (!carga.signal.aborted) setDocumentos(resultados)
      })
      .catch(() => {
        if (!carga.signal.aborted) {
          setErro(ERRO_CARREGAMENTO)
          setDocumentos([])
        }
      })
      .finally(() => {
        if (!carga.signal.aborted) setCarregando(false)
      })

    return () => carga.abort()
  }, [])

  useEffect(() => {
    if (!documentoSelecionado) return

    const overflowAnterior = document.body.style.overflow
    const fecharComEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setDocumentoSelecionado(null)
    }

    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', fecharComEscape)

    return () => {
      document.body.style.overflow = overflowAnterior
      document.removeEventListener('keydown', fecharComEscape)
    }
  }, [documentoSelecionado])

  const tribunaisDisponiveis = useMemo(() => {
    const siglas = new Set(
      documentos.map((d) => d.tribunal).filter((t): t is string => Boolean(t)),
    )
    return Array.from(siglas).sort()
  }, [documentos])

  const fontesDisponiveis = useMemo(() => {
    const siglas = new Set(documentos.map((d) => d.fonte).filter(Boolean))
    return Array.from(siglas).sort()
  }, [documentos])

  const resultados = useMemo(() => {
    const filtrados = documentos.filter((doc) => {
      if (!filtros[doc.type]) return false

      if (fonte !== TODAS_AS_FONTES && doc.fonte !== fonte) {
        return false
      }

      if (tribunal !== TODOS_OS_TRIBUNAIS && doc.tribunal !== tribunal) {
        return false
      }

      // Resultado e órgão julgador só existem em decisões: com uma barra
      // selecionada, precedentes e doutrina saem da lista.
      if (selecao.resultado || selecao.orgaoJulgador) {
        if (doc.type !== 'jurisprudencia') return false
        if (selecao.resultado && doc.resultado !== selecao.resultado) return false
        if (
          selecao.orgaoJulgador &&
          orgaoDoGrafico(doc.orgaoJulgador) !== selecao.orgaoJulgador
        ) {
          return false
        }
      }

      const dataReferencia = dataDoDocumento(doc)
      if ((dataDe || dataAte) && dataReferencia) {
        const docDate = new Date(dataReferencia)
        if (dataDe && docDate < new Date(dataDe)) return false
        if (dataAte && docDate > new Date(dataAte)) return false
      }

      return true
    })

    return filtrados.sort((a, b) => {
      const dataAValor = dataDoDocumento(a)
      const dataBValor = dataDoDocumento(b)
      const dataA = dataAValor ? new Date(dataAValor).getTime() : NaN
      const dataB = dataBValor ? new Date(dataBValor).getTime() : NaN
      const aSemData = Number.isNaN(dataA)
      const bSemData = Number.isNaN(dataB)

      if (aSemData || bSemData) {
        return Number(aSemData) - Number(bSemData)
      }

      return ordenacao === 'mais-recentes' ? dataB - dataA : dataA - dataB
    })
  }, [documentos, filtros, fonte, tribunal, dataDe, dataAte, selecao, ordenacao])

  async function exportarPdf(paginaDe: number, paginaAte: number) {
    if (carregando || exportando || erro || !resultados.length) return
    setErroExportacao(null)
    setExportacaoConcluida(false)
    const selecionados = resultados.slice((paginaDe - 1) * ITENS_POR_PAGINA, paginaAte * ITENS_POR_PAGINA)
    if (!Number.isInteger(paginaDe) || !Number.isInteger(paginaAte) || paginaDe < 1 || paginaDe > paginaAte || paginaAte > totalPaginas || !selecionados.length) return
    if (selecionados.length > 5000) {
      setErroExportacao('Selecione um intervalo com até 5.000 documentos por PDF.')
      return
    }
    setExportando(true)
    try {
      const response = await fetch(`${API_BASE}/api/pesquisas/exportacoes/pdf`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          termo: termoPesquisado,
          categorias: (Object.keys(filtros) as DocType[])
            .filter((tipo) => filtros[tipo]).map((tipo) => CATEGORIA_POR_TIPO[tipo]),
          fonte: fonte === TODAS_AS_FONTES ? null : fonte,
          tribunal: tribunal === TODOS_OS_TRIBUNAIS ? null : tribunal,
          dataDe: dataDe || null,
          dataAte: dataAte || null,
          ordenacao,
          intervaloPaginas: { de: paginaDe, ate: paginaAte, totalResultados: resultados.length, itensPorPagina: ITENS_POR_PAGINA },
          resultados: selecionados.map((documento) => ({
            categoria: CATEGORIA_POR_TIPO[documento.type], id: documento.id,
          })),
        }),
      })
      if (!response.ok) {
        if (response.status === 409) throw new Error('Um resultado não está mais disponível. Atualize a pesquisa e tente novamente.')
        if (response.status === 413) throw new Error('O relatório é muito grande. Refine os filtros e tente novamente.')
        if (response.status === 400) throw new Error('Confira os filtros e o período da pesquisa antes de exportar.')
        throw new Error('Não foi possível gerar o PDF. Tente novamente.')
      }
      const pdf = await response.blob()
      const signature = await pdf.slice(0, 5).text()
      if (!response.headers.get('Content-Type')?.includes('application/pdf') || signature !== '%PDF-') {
        throw new Error('O arquivo recebido não é um PDF válido. Tente novamente.')
      }
      const filename = response.headers.get('Content-Disposition')?.match(/filename="?(wikilaw-[\w.-]+\.pdf)"?/i)?.[1]
        ?? `wikilaw-pesquisa-${new Date().toISOString().slice(0, 10)}.pdf`
      const url = URL.createObjectURL(pdf)
      const link = document.createElement('a')
      link.href = url
      link.download = filename
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
      setModalExportacao(false)
      setExportacaoConcluida(true)
    } catch (error) {
      setErroExportacao(error instanceof Error ? error.message : 'Não foi possível baixar o PDF. Tente novamente.')
    } finally {
      setExportando(false)
    }
  }

  const totalPaginas = Math.max(
    1,
    Math.ceil(resultados.length / ITENS_POR_PAGINA),
  )
  const paginaExibida = Math.min(paginaAtual, totalPaginas)
  const resultadosPaginados = resultados.slice(
    (paginaExibida - 1) * ITENS_POR_PAGINA,
    paginaExibida * ITENS_POR_PAGINA,
  )

  function voltarParaPrimeiraPagina() {
    setPaginaAtual(1)
    setDocumentoSelecionado(null)
    setErroExportacao(null)
    setExportacaoConcluida(false)
  }

  function toggleFiltro(type: DocType) {
    // Sem jurisprudência os gráficos ficam vazios, então a seleção neles perde o sentido.
    if (type === 'jurisprudencia' && filtros.jurisprudencia) {
      setSelecao(SEM_SELECAO)
    }
    setFiltros((prev) => ({ ...prev, [type]: !prev[type] }))
    voltarParaPrimeiraPagina()
  }

  // Clicar na barra selecionada desfaz o filtro. "Outros" agrupa vários órgãos
  // e barra vazia não traria resultado, então nenhuma das duas é selecionável.
  function alternarSelecao(grafico: keyof SelecaoGraficos, item: DistribuicaoItem) {
    if (selecao[grafico] === item.categoria) {
      limparSelecao(grafico)
    } else if (item.categoria !== OUTROS && item.quantidade > 0) {
      setSelecao((prev) => ({ ...prev, [grafico]: item.categoria }))
      voltarParaPrimeiraPagina()
    }
  }

  function limparSelecao(grafico: keyof SelecaoGraficos) {
    setSelecao((prev) => ({ ...prev, [grafico]: null }))
    voltarParaPrimeiraPagina()
  }

  function limparFiltros() {
    setFiltros(TODOS_OS_TIPOS)
    setFonte(TODAS_AS_FONTES)
    setTribunal(TODOS_OS_TRIBUNAIS)
    setDataDe('')
    setDataAte('')
    setSelecao(SEM_SELECAO)
    voltarParaPrimeiraPagina()
  }

  // Resumo do que está filtrando a lista, visível mesmo com o painel fechado.
  const filtrosAtivos: FiltroAtivo[] = []
  const tiposMarcados = (Object.keys(filtros) as DocType[]).filter((tipo) => filtros[tipo])
  if (tiposMarcados.length < Object.keys(filtros).length) {
    filtrosAtivos.push({
      chave: 'tipos',
      rotulo: tiposMarcados.length
        ? `Tipo: ${tiposMarcados.map((tipo) => ROTULO_POR_TIPO[tipo]).join(', ')}`
        : 'Nenhum tipo',
      remover: () => setFiltros(TODOS_OS_TIPOS),
    })
  }
  if (fonte !== TODAS_AS_FONTES) {
    filtrosAtivos.push({ chave: 'fonte', rotulo: `Fonte: ${fonte}`, remover: () => setFonte(TODAS_AS_FONTES) })
  }
  if (tribunal !== TODOS_OS_TRIBUNAIS) {
    filtrosAtivos.push({
      chave: 'tribunal',
      rotulo: `Tribunal: ${tribunal}`,
      remover: () => setTribunal(TODOS_OS_TRIBUNAIS),
    })
  }
  if (dataDe) {
    filtrosAtivos.push({ chave: 'dataDe', rotulo: `De ${formatarDataCurta(dataDe)}`, remover: () => setDataDe('') })
  }
  if (dataAte) {
    filtrosAtivos.push({ chave: 'dataAte', rotulo: `Até ${formatarDataCurta(dataAte)}`, remover: () => setDataAte('') })
  }
  if (selecao.resultado) {
    filtrosAtivos.push({
      chave: 'resultado',
      rotulo: `Resultado: ${selecao.resultado}`,
      remover: () => setSelecao((prev) => ({ ...prev, resultado: null })),
    })
  }
  if (selecao.orgaoJulgador) {
    filtrosAtivos.push({
      chave: 'orgaoJulgador',
      rotulo: `Órgão: ${selecao.orgaoJulgador}`,
      remover: () => setSelecao((prev) => ({ ...prev, orgaoJulgador: null })),
    })
  }

  // A mesma busca aparece no hero e na barra fixa (as duas compartilham o
  // texto digitado); os ids mudam para não repetir no DOM durante a transição.
  const formularioBusca = (origem: 'destaque' | 'barra') => (
    <form
      className="search-form"
      role="search"
      onSubmit={(event) => {
        event.preventDefault()
        void buscar()
      }}
    >
      <div className="search-box">
        <input
          type="text"
          maxLength={300}
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            if (erroValidacao) setErroValidacao(null)
          }}
          aria-label="Descrição do caso"
          aria-invalid={Boolean(erroValidacao)}
          aria-describedby={erroValidacao ? `search-error-${origem}` : undefined}
          placeholder="Ex.: dano moral por negativação indevida do nome do consumidor..."
        />
        {/* Fica habilitado durante a carga: uma nova pesquisa substitui a anterior. */}
        <button type="submit" aria-busy={carregando}>
          PESQUISAR
        </button>
      </div>
      {erroValidacao && (
        <p id={`search-error-${origem}`} className="search-error" role="alert">
          {erroValidacao}
        </p>
      )}
    </form>
  )

  return (
    <>
      <header className="header">
        <div className="brand">
          <div className="brand-mark">W</div>
          <div className="brand-text">
            <div className="name">Wikilaw</div>
            <div className="tag">PESQUISA JURÍDICA</div>
          </div>
        </div>
      </header>

      <section className="hero">
        <div className="hero-rule" />
        <h1>
          Descreva o seu caso.{' '}
          <em>
            Nós
            <br />
            encontramos o direito.
          </em>
        </h1>
        <p>
          Pesquisa em linguagem natural entre jurisprudências, precedentes e
          doutrinas dos principais tribunais do país.
        </p>
        {/* Já encaixada na barra, esta cópia está fora da tela: sai do foco e
            dos leitores de tela para não haver duas buscas ativas. */}
        <div
          className="busca-destaque"
          ref={buscaDestaqueRef}
          inert={encaixe === 'barra'}
        >
          {formularioBusca('destaque')}
        </div>
      </section>

      <div className="layout">
        <div className="barra-fixa" ref={faixaRef}>
          <section className="toolbar" aria-label="Busca e filtros">
            <div className="toolbar-row">
              {encaixe !== 'destaque' && (
                <div className="busca-encaixe" data-fase={encaixe}>
                  {formularioBusca('barra')}
                </div>
              )}
              {filtrosAtivos.length > 0 && (
                <>
                  <ul className="active-filters" aria-label="Filtros ativos">
                    {filtrosAtivos.map((filtro) => (
                      <li key={filtro.chave}>
                        <button
                          type="button"
                          className="chip"
                          aria-label={`Remover filtro ${filtro.rotulo}`}
                          onClick={() => {
                            filtro.remover()
                            voltarParaPrimeiraPagina()
                          }}
                        >
                          {filtro.rotulo}
                          <span aria-hidden="true"> ×</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                  <button type="button" className="clear-link" onClick={limparFiltros}>
                    Limpar filtros
                  </button>
                </>
              )}

              <button
                type="button"
                className="filters-toggle"
                aria-expanded={filtrosAbertos}
                aria-controls="painel-filtros"
                onClick={() => setFiltrosAbertos((aberto) => !aberto)}
              >
                <span aria-hidden="true">▤</span> <span className="rotulo-filtros">Filtros</span>
                {filtrosAtivos.length > 0 && (
                  <span className="filters-count">{filtrosAtivos.length}</span>
                )}
                <span className="chevron" aria-hidden="true">▾</span>
              </button>
            </div>

            {filtrosAbertos && (
              <div id="painel-filtros" className="filters">
                <fieldset className="group doc-types">
                  <legend className="group-label">TIPO DE DOCUMENTO</legend>
                  <div className="check-row">
                    {(Object.keys(ROTULO_POR_TIPO) as DocType[]).map((tipo) => (
                      <label className="check" key={tipo}>
                        <input
                          type="checkbox"
                          checked={filtros[tipo]}
                          onChange={() => toggleFiltro(tipo)}
                        />
                        {ROTULO_POR_TIPO[tipo]}
                      </label>
                    ))}
                  </div>
                </fieldset>

                <div className="group">
                  <label className="group-label" htmlFor="filtro-fonte">FONTE</label>
                  <select
                    id="filtro-fonte"
                    value={fonte}
                    onChange={(e) => {
                      setFonte(e.target.value)
                      voltarParaPrimeiraPagina()
                    }}
                  >
                    <option>{TODAS_AS_FONTES}</option>
                    {fontesDisponiveis.map((sigla) => (
                      <option key={sigla}>{sigla}</option>
                    ))}
                  </select>
                </div>

                <div className="group">
                  <label className="group-label" htmlFor="filtro-tribunal">TRIBUNAL</label>
                  <select
                    id="filtro-tribunal"
                    value={tribunal}
                    onChange={(e) => {
                      setTribunal(e.target.value)
                      voltarParaPrimeiraPagina()
                    }}
                  >
                    <option>{TODOS_OS_TRIBUNAIS}</option>
                    {tribunaisDisponiveis.map((sigla) => (
                      <option key={sigla}>{sigla}</option>
                    ))}
                  </select>
                </div>

                <fieldset className="group">
                  <legend className="group-label">PERÍODO</legend>
                  <div className="date-row">
                    <div>
                      <label htmlFor="filtro-data-de">De</label>
                      <input
                        id="filtro-data-de"
                        className="date-input"
                        type="date"
                        value={dataDe}
                        onChange={(e) => {
                          setDataDe(e.target.value)
                          voltarParaPrimeiraPagina()
                        }}
                      />
                    </div>
                    <div>
                      <label htmlFor="filtro-data-ate">Até</label>
                      <input
                        id="filtro-data-ate"
                        className="date-input"
                        type="date"
                        value={dataAte}
                        onChange={(e) => {
                          setDataAte(e.target.value)
                          voltarParaPrimeiraPagina()
                        }}
                      />
                    </div>
                  </div>
                </fieldset>
              </div>
            )}
          </section>
        </div>

        <div className="content">
          {/* Fica ao lado da lista (e acima dela em telas estreitas): clicar
              numa barra mostra o efeito na lista sem precisar rolar. */}
                    <aside className="panorama" aria-label="Panorama da pesquisa">
            <div className="sort-options escopo-painel" role="group" aria-label="O que o painel mostra">
              <button
                type="button"
                aria-pressed={escopo === 'pesquisa'}
                onClick={() => setEscopo('pesquisa')}
              >
                Pesquisa
              </button>
              <button
                type="button"
                aria-pressed={escopo === 'panorama'}
                onClick={() => setEscopo('panorama')}
              >
                Panorama do tribunal
              </button>
            </div>
            {escopo === 'pesquisa' ? (
              <>
                {!erro && !carregando && indicadores && (
                  <section className="indicadores" aria-label="Indicadores da pesquisa">
                    <div className="indicador">
                      <span className="indicador-label">ADERÊNCIA</span>
                      <strong className="indicador-valor">
                        {indicadores.aderencia === null
                          ? '—'
                          : `${indicadores.aderencia}%`}
                      </strong>
                      <span className="indicador-detalhe">
                        {indicadores.classificadas
                          ? `${indicadores.favoraveis} de ${indicadores.classificadas} decisões favoráveis ao recurso`
                          : 'Nenhuma decisão com resultado identificado'}
                      </span>
                    </div>
                    <div className="indicador">
                      <span className="indicador-label">DECISÕES ANALISADAS</span>
                      <strong className="indicador-valor">
                        {indicadores.totalDecisoes}
                      </strong>
                      <span className="indicador-detalhe">
                        {indicadores.classificadas} com resultado identificado
                      </span>
                    </div>
                  </section>
                )}
                <GraficoDashboard
                  titulo="Resultado dos recursos"
                  dados={dashboard.dados?.distribuicaoMerito}
                  carregando={dashboard.carregando}
                  erro={dashboard.erro}
                  selecionada={selecao.resultado}
                  onSelecionar={(item) => alternarSelecao('resultado', item)}
                  onLimparSelecao={() => limparSelecao('resultado')}
                />
                <GraficoDashboard
                  titulo="Decisões por órgão julgador"
                  dados={dashboard.dados?.distribuicaoCamaraTurma}
                  carregando={dashboard.carregando}
                  erro={dashboard.erro}
                  horizontal
                  selecionada={selecao.orgaoJulgador}
                  onSelecionar={(item) => alternarSelecao('orgaoJulgador', item)}
                  onLimparSelecao={() => limparSelecao('orgaoJulgador')}
                />
                <GraficoDashboard
                  titulo="Evolução Temporal da Tese"
                  dados={dashboard.dados?.evolucaoAnual}
                  carregando={dashboard.carregando}
                  erro={dashboard.erro}
                />
              </>
            ) : (
              <PanoramaTribunal
                tribunal={tribunal === TODOS_OS_TRIBUNAIS ? null : tribunal}
              />
            )}
          </aside>

          <main className="results">
            <div className="results-header">
              <div className="results-summary">
                <h2>Resultados da pesquisa</h2>
                <span className="results-count">
                  {carregando
                    ? 'Buscando...'
                    : `${resultados.length} documentos encontrados · Página ${paginaExibida} de ${totalPaginas}`}
                </span>
              </div>
              <div className="results-actions" role="group" aria-label="Ações dos resultados">
                <div className="sort-control" role="group" aria-label="Ordenar resultados por data de publicação">
                  <span className="sort-label">ORDENAR POR DATA</span>
                  <div className="sort-options">
                    <button
                      type="button"
                      aria-pressed={ordenacao === 'mais-recentes'}
                      onClick={() => {
                        setOrdenacao('mais-recentes')
                        voltarParaPrimeiraPagina()
                      }}
                    >
                      Mais recentes
                    </button>
                    <button
                      type="button"
                      aria-pressed={ordenacao === 'mais-antigos'}
                      onClick={() => {
                        setOrdenacao('mais-antigos')
                        voltarParaPrimeiraPagina()
                      }}
                    >
                      Mais antigos
                    </button>
                  </div>
                </div>
                <button
                  type="button"
                  className="export-pdf-btn"
                  ref={botaoExportar}
                  disabled={carregando || exportando || Boolean(erro) || resultados.length === 0}
                  onClick={() => { setErroExportacao(null); setExportacaoConcluida(false); setModalExportacao(true) }}
                  aria-haspopup="dialog"
                >
                  Exportar PDF
                </button>
              </div>
            </div>

            {erroExportacao && !modalExportacao && <p className="export-message export-error" role="alert">{erroExportacao}</p>}
            {exportacaoConcluida && <p className="export-message" role="status">PDF gerado. O download foi iniciado.</p>}

            {erro && <div className="empty">{erro}</div>}

            {!erro && !carregando && resultados.length === 0 && (
              <div className="empty">
                Nenhum documento encontrado com os filtros selecionados.
              </div>
            )}

            {!erro &&
              resultadosPaginados.map((doc) => {
                const chave = `${doc.type}-${doc.id}`

                return (
                  <article className="card" key={chave}>
                    <div className="card-top">
                      <span
                        className={
                          'badge' +
                          (doc.type === 'precedente' ? ' precedente' : '') +
                          (doc.type === 'doutrina' ? ' doutrina' : '')
                        }
                      >
                        {doc.badge}
                      </span>
                      <span className="proc-num">{doc.fonte}</span>
                    </div>
                    <h3>{doc.title}</h3>
                    {doc.type === 'jurisprudencia' && (
                      <div className="decision-highlight">
                        <span className="decision-label">DECISÃO</span>
                        <strong>
                          {doc.decisao ?? 'Decisão não informada pela fonte.'}
                        </strong>
                      </div>
                    )}
                    <div className="document-content">
                      <h4>Ementa</h4>
                      <p className="ementa">{doc.text}</p>
                    </div>
                    <button
                      type="button"
                      className="details-toggle"
                      aria-haspopup="dialog"
                      onClick={() => setDocumentoSelecionado(doc)}
                    >
                      VER DETALHES
                    </button>
                    <div className="card-bottom">
                      <div className="card-meta">
                        <span>
                          ⚖{' '}
                          {[doc.tribunal, doc.orgaoJulgador]
                            .filter(Boolean)
                            .join(' — ') || doc.fonte}
                        </span>
                        <span>🗓 {formatarData(dataDoDocumento(doc))}</span>
                      </div>
                      <AcessoFonte url={doc.urlOriginal} className="source-link" />
                    </div>
                  </article>
                )
              })}

            {!erro && !carregando && totalPaginas > 1 && (
              <div
                className="pagination"
                role="navigation"
                aria-label="Páginas de resultados"
              >
                <button
                  type="button"
                  disabled={paginaExibida === 1}
                  onClick={() => {
                    setPaginaAtual((pagina) => Math.max(1, pagina - 1))
                    setDocumentoSelecionado(null)
                  }}
                >
                  ANTERIOR
                </button>
                {paginasVisiveis(paginaExibida, totalPaginas).map((pagina, index) =>
                  pagina === RETICENCIAS ? (
                    <span
                      className="pagination-ellipsis"
                      aria-hidden="true"
                      key={`reticencias-${index}`}
                    >
                      {RETICENCIAS}
                    </span>
                  ) : (
                    <button
                      type="button"
                      className={pagina === paginaExibida ? 'active' : ''}
                      aria-current={pagina === paginaExibida ? 'page' : undefined}
                      key={pagina}
                      onClick={() => {
                        setPaginaAtual(pagina)
                        setDocumentoSelecionado(null)
                      }}
                    >
                      {pagina}
                    </button>
                  ),
                )}
                <button
                  type="button"
                  disabled={paginaExibida === totalPaginas}
                  onClick={() => {
                    setPaginaAtual((pagina) =>
                      Math.min(totalPaginas, pagina + 1),
                    )
                    setDocumentoSelecionado(null)
                  }}
                >
                  PRÓXIMA
                </button>
              </div>
            )}
          </main>
        </div>
      </div>

      {modalExportacao && (
        <ExportPdfDialog
          totalResults={resultados.length}
          pageSize={ITENS_POR_PAGINA}
          pending={exportando}
          error={erroExportacao}
          returnFocusTo={botaoExportar}
          onClose={() => setModalExportacao(false)}
          onExport={(de, ate) => void exportarPdf(de, ate)}
        />
      )}

      {documentoSelecionado && (
        <div
          className="modal-overlay"
          role="presentation"
          onMouseDown={() => setDocumentoSelecionado(null)}
        >
          <section
            className="details-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="modal-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              className="modal-close"
              aria-label="Fechar detalhes"
              autoFocus
              onClick={() => setDocumentoSelecionado(null)}
            >
              ×
            </button>

            <div className="modal-heading">
              <span
                className={
                  'badge' +
                  (documentoSelecionado.type === 'precedente'
                    ? ' precedente'
                    : '') +
                  (documentoSelecionado.type === 'doutrina' ? ' doutrina' : '')
                }
              >
                {documentoSelecionado.badge}
              </span>
              <span className="proc-num">{documentoSelecionado.fonte}</span>
              <h2 id="modal-title">{documentoSelecionado.title}</h2>
            </div>

            <dl className="details-grid">
              {documentoSelecionado.tipoDocumento && (
                <div>
                  <dt>Tipo</dt>
                  <dd>{documentoSelecionado.tipoDocumento}</dd>
                </div>
              )}
              {documentoSelecionado.numeroProcessoOuTema && (
                <div>
                  <dt>
                    {documentoSelecionado.type === 'precedente'
                      ? 'Tema'
                      : 'Processo'}
                  </dt>
                  <dd>{documentoSelecionado.numeroProcessoOuTema}</dd>
                </div>
              )}
              <div>
                <dt>Fonte</dt>
                <dd>{documentoSelecionado.fonte}</dd>
              </div>
              {documentoSelecionado.tribunal && (
                <div>
                  <dt>Tribunal</dt>
                  <dd>{documentoSelecionado.tribunal}</dd>
                </div>
              )}
              {documentoSelecionado.orgaoJulgador && (
                <div>
                  <dt>Órgão julgador</dt>
                  <dd>{documentoSelecionado.orgaoJulgador}</dd>
                </div>
              )}
              {documentoSelecionado.autoresOuRelator && (
                <div>
                  <dt>
                    {documentoSelecionado.type === 'doutrina'
                      ? 'Autores'
                      : 'Relator'}
                  </dt>
                  <dd>{documentoSelecionado.autoresOuRelator}</dd>
                </div>
              )}
              {documentoSelecionado.dataJulgamento && (
                <div>
                  <dt>Data do julgamento</dt>
                  <dd>{formatarData(documentoSelecionado.dataJulgamento)}</dd>
                </div>
              )}
              <div>
                <dt>Data da publicação</dt>
                <dd>{formatarData(documentoSelecionado.dataPublicacao)}</dd>
              </div>
              {documentoSelecionado.dataOriginal && (
                <div>
                  <dt>Data informada pela fonte</dt>
                  <dd>{documentoSelecionado.dataOriginal}</dd>
                </div>
              )}
            </dl>

            {documentoSelecionado.type === 'jurisprudencia' && (
              <div className="modal-decision">
                <span className="decision-label">DECISÃO</span>
                <p>
                  {documentoSelecionado.decisao ??
                    'Decisão não informada pela fonte.'}
                </p>
              </div>
            )}

            <div className="modal-summary">
              <h3>Ementa</h3>
              <p>{documentoSelecionado.text}</p>
            </div>

            <footer className="modal-footer">
              <AcessoFonte
                url={documentoSelecionado.urlOriginal}
                className="source-button"
              />
            </footer>
          </section>
        </div>
      )}
    </>
  )
}

export default App
