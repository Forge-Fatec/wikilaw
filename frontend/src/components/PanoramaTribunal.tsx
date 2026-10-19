import { BarChartDistribuicao } from './charts/BarChartDistribuicao'
import { usePanoramaTribunal } from './hooks/usePanoramaTribunal'

// Panorama geral do tribunal selecionado nos filtros: todas as decisões dele,
// sem o termo da pesquisa, para comparar o caso com o comportamento do tribunal.
export function PanoramaTribunal({ tribunal }: { tribunal: string | null }) {
  const { dados, carregando, erro } = usePanoramaTribunal(tribunal)
  const indicadores = dados?.indicadores
  const merito = dados?.distribuicaoMerito ?? []
  const vazio = !merito.some((item) => item.quantidade > 0)

  return (
    <section className="panorama-tribunal" aria-label="Panorama geral do tribunal">
      <header className="panorama-tribunal-cabecalho">
        <span className="panorama-tribunal-rotulo">PANORAMA GERAL DO TRIBUNAL</span>
        {tribunal ? (
          <>
            <h2>{tribunal}</h2>
            <p>Todas as decisões do tribunal, sem o termo da pesquisa.</p>
          </>
        ) : (
          <p>Selecione um tribunal nos filtros para ver o panorama geral dele.</p>
        )}
      </header>

      {tribunal && indicadores && (
        <section className="indicadores" aria-label="Indicadores do tribunal">
          <div className="indicador">
            <span className="indicador-label">ADERÊNCIA</span>
            <strong className="indicador-valor">
              {indicadores.aderencia === null ? '—' : `${indicadores.aderencia}%`}
            </strong>
            <span className="indicador-detalhe">
              {indicadores.classificadas
                ? `${indicadores.favoraveis} de ${indicadores.classificadas} decisões favoráveis ao recurso`
                : 'Nenhuma decisão com resultado identificado'}
            </span>
          </div>
          <div className="indicador">
            <span className="indicador-label">DECISÕES ANALISADAS</span>
            <strong className="indicador-valor">{indicadores.totalDecisoes}</strong>
            <span className="indicador-detalhe">
              {indicadores.classificadas} com resultado identificado
            </span>
          </div>
        </section>
      )}

      {tribunal && (
        <div className="chart-container">
          <h3>Decisões do tribunal por resultado</h3>
          {carregando ? (
            <p className="chart-loading">Carregando gráfico...</p>
          ) : erro ? (
            <p className="chart-error">{erro}</p>
          ) : vazio ? (
            <p className="chart-loading">Nenhuma decisão neste tribunal.</p>
          ) : (
            <BarChartDistribuicao data={merito} />
          )}
        </div>
      )}
    </section>
  )
}