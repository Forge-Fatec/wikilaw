import type { Fonte } from './hooks/useFontes'

const AVISO_ULTIMA_FONTE = 'Ao menos uma fonte precisa ficar selecionada.'

// Um checkbox por fonte, todas marcadas de início. A última marcada fica
// travada: a pesquisa nunca roda sem nenhuma fonte.
export function FiltroFontes({
  fontes,
  desmarcadas,
  carregando,
  erro,
  onAlternar,
}: {
  fontes: Fonte[]
  desmarcadas: string[]
  carregando: boolean
  erro: string | null
  onAlternar: (sigla: string) => void
}) {
  const marcadas = fontes.filter((fonte) => !desmarcadas.includes(fonte.sigla))
  const ultimaMarcada = marcadas.length === 1 ? marcadas[0].sigla : null

  return (
    <fieldset className="filtro-fontes">
      <legend>FONTES PESQUISADAS</legend>
      {carregando ? (
        <p className="filtro-fontes-aviso">Carregando fontes...</p>
      ) : erro ? (
        <p className="filtro-fontes-aviso" role="alert">
          {erro}
        </p>
      ) : (
        <>
          <div className="filtro-fontes-opcoes">
            {fontes.map((fonte) => {
              const travada = fonte.sigla === ultimaMarcada
              return (
                <label
                  className={'check' + (travada ? ' travada' : '')}
                  key={fonte.sigla}
                  title={travada ? AVISO_ULTIMA_FONTE : fonte.nome}
                >
                  <input
                    type="checkbox"
                    checked={!desmarcadas.includes(fonte.sigla)}
                    disabled={travada}
                    aria-describedby={travada ? 'aviso-ultima-fonte' : undefined}
                    onChange={() => onAlternar(fonte.sigla)}
                  />
                  {fonte.sigla}
                </label>
              )
            })}
          </div>
          {ultimaMarcada && (
            <p id="aviso-ultima-fonte" className="filtro-fontes-aviso">
              {AVISO_ULTIMA_FONTE}
            </p>
          )}
        </>
      )}
    </fieldset>
  )
}
