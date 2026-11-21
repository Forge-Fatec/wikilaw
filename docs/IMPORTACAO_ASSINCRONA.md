# Importação em segundo plano — SCRUM-244 / SCRUM-249

Os endpoints de importação retornam **HTTP 202**, `idTarefa` e o header `Location`.
O retorno confirma o enfileiramento. Consulte `/api/integrations/tarefas/{idTarefa}`
para acompanhar o processamento; pesquisas do site continuam lendo o PostgreSQL.
Reinicie o backend atualizado para o Flyway aplicar V8/V9 e preparar `import_job`.

## Carga completa automática

Por padrão, ao iniciar o backend, são preparadas cargas completas das fontes integradas.
O worker segue a continuação **até a fonte encerrar os resultados**, sem parar após três
páginas e sem orçamento máximo de páginas no modo completo. O tamanho de cada página
continua limitado, e há uma página por vez por instância.

- TJDFT/BDJur: pesquisa sem termo.
- STJ: enumera os datasets de acórdãos de todos os órgãos e percorre seus recursos JSON/ZIP,
  incluindo arquivos históricos. Trocar arquivo reinicia o offset, sem perder o checkpoint.
- STJ_PRECEDENTES: percorre todos os temas do CSV e preserva os vínculos com Processos.csv.
- Pangea: percorre todas as combinações de órgão/tipo do catálogo, sem termo, incluindo
  cancelados. Não usa o total de 10000 como prova de fim; segue as páginas da partição.
- SciELO: remove a restrição à revista Direito GV e à coleção `scl`.
- BDTD: colhe registros OAI sem filtro por assunto, incluindo materiais de outras áreas.
- DataJud: prepara TJSP/TJRJ/TJMG automaticamente se `DATAJUD_API_KEY` estiver configurada.
  Outros tribunais ainda não são integrados pelo enum do projeto.

`ateEsgotar=true` no progresso identifica uma tarefa sem orçamento de páginas; nesse caso,
`maximoPaginas` é ignorado. Uma reinicialização reutiliza a tarefa e seu checkpoint. Uma
carga inicial concluída não é recriada a cada boot. Tarefas completas canceladas/falhas
permanecem registradas; cancelamento é respeitado e falhas podem ser retomadas pelo endpoint.

As antigas amostras pendentes/limitadas são substituídas por uma carga completa desde o
início, preservando registros e auditoria. Se uma página antiga estiver em andamento,
a preparação aguarda sua liberação, com reconciliação a cada 30 segundos. Essa rotina
não é um agendamento de atualizações: apenas garante o plano da primeira carga completa.

Configuração padrão:

```dotenv
WIKILAW_BOOTSTRAP_JURISPRUDENCIA_ENABLED=true
WIKILAW_BOOTSTRAP_JURISPRUDENCIA_COMPLETO=true
```

`COMPLETO=false` restaura a carga de amostra, usando termo/páginas configurados.
`SOMENTE_SE_VAZIO` aplica-se apenas à amostra: alguns registros existentes não comprovam
que o acervo já foi colhido. `ENABLED=false` desativa a preparação automática de todas
as fontes, incluindo DataJud, na inicialização. Para desligar também as atualizações
periódicas, configure `WIKILAW_REFRESH_ENABLED=false`. A API continua permitindo tarefas manuais.

## Atualização periódica — SCRUM-249

O agendador verifica as fontes a cada minuto e prepara uma nova tarefa quando passou
o intervalo desde a última tarefa encerrada daquela fonte (ou tribunal DataJud).
O histórico em `import_job` mantém esse controle após reiniciar o backend.
Sem histórico, a primeira verificação pode preparar uma carga completa, mesmo com o
bootstrap desativado. Os dois agendadores possuem configurações independentes.

```dotenv
WIKILAW_REFRESH_ENABLED=true
WIKILAW_REFRESH_INTERVAL=24h
WIKILAW_REFRESH_PAGE_SIZE=20
WIKILAW_REFRESH_INITIAL_DELAY=60000
WIKILAW_REFRESH_POLL_DELAY=60000
```

O intervalo aceita durações como `24h`, `7d` e `PT24H`, com mínimo de um minuto;
os delays são em milissegundos. O tamanho de página aceita 1 a 100.

- DataJud: coleta incremental até esgotar a janela, com limite superior congelado.
  Usa a última carga sem filtros concluída sem erros, sobrepondo cinco minutos para
  tolerar indexação tardia. Sem histórico válido, começa sem limite inferior.
  Sem `DATAJUD_API_KEY`, apenas as fontes documentais são atualizadas.
- Fontes documentais: percorrem novamente o acervo suportado, com novos snapshots e
  cache vazio. As chaves naturais atualizam registros existentes sem duplicá-los.
  A releitura completa é necessária porque os adaptadores não possuem um filtro
  uniforme por data de atualização; considere o custo de downloads ao definir o intervalo.
- Tarefas pendentes, em execução, com falha ou limite atingido impedem uma nova tarefa
  no mesmo escopo. Falhas preservam a continuação e exigem retomada ou cancelamento
  pelo administrador. Os retries HTTP existentes continuam sendo utilizados.
- Cancelar uma tarefa adia a próxima coleta desse escopo pelo intervalo configurado;
  desligue `WIKILAW_REFRESH_ENABLED` para suspender as atualizações periódicas.
- Uma falha ao preparar uma fonte é registrada no log e não impede as demais.
  O índice único do PostgreSQL impede duas tarefas ativas do mesmo escopo entre réplicas.

O processamento continua dependendo de `WIKILAW_IMPORT_WORKER_ENABLED=true`.
Consulte o progresso, erros e contadores pelos endpoints de tarefas abaixo, ou use
a consulta SQL deste guia para encontrar os IDs das tarefas automáticas.

## Disparo manual

Também é possível disparar manualmente:

```powershell
$base = 'http://localhost:8080'
# Todo o acervo documental suportado pelo coletor, sem restrição por assunto.
Invoke-RestMethod "$base/api/integrations/documentos/STJ/import" -Method Post `
    -ContentType 'application/json' -Body '{"acervoCompleto":true,"tamanhoPagina":100}'
# Esgotar os resultados do DataJud (ou de uma consulta com filtros).
Invoke-RestMethod "$base/api/integrations/datajud/import?ateEsgotar=true" -Method Post `
    -ContentType 'application/json' -Body '{"tribunal":"TJSP","tamanhoPagina":100}'
```

A coleta abrange o que as interfaces públicas permitem obter. Bloqueios, tokens expirados,
limites de paginação impostos pela fonte e arquivos inválidos podem impedir uma coleta
integral. BDTD pode retornar verificação de navegador. No STJ, arquivos incompatíveis ou
acima do limite de 32 MiB por download são registrados como erro e os demais recursos
continuam. Isso resulta em `CONCLUIDA_COM_ERROS`, nunca em completude sem ressalvas. O bruto
recebido fica auditado (ZIP como base64); respostas que excedem o limite podem ser interrompidas
antes de haver um payload completo. Erros de transporte ainda podem deixar a tarefa em FALHA.

Não há garantia de obter dados privados, indisponíveis ou além do que a fonte permite.
As buscas do usuário funcionam sobre os registros já gravados; a base cresce durante a carga.
Para uma base pronta no primeiro acesso, execute a carga antes de liberar o sistema aos usuários.

## Executar e acompanhar

```powershell
$base = 'http://localhost:8080'
$tarefa = Invoke-RestMethod "$base/api/integrations/datajud/import" `
    -Method Post -ContentType 'application/json' `
    -Body '{"tribunal":"TJSP","tamanhoPagina":100,"maximoPaginas":50}'
Invoke-RestMethod "$base/api/integrations/tarefas/$($tarefa.idTarefa)"

$documentos = Invoke-RestMethod "$base/api/integrations/documentos/STJ/import?maximoPaginas=50" `
    -Method Post -ContentType 'application/json' -Body '{"offset":0,"tamanhoPagina":100}'
Invoke-RestMethod "$base/api/integrations/tarefas/$($documentos.idTarefa)"

# Retomar falha ou adicionar páginas a uma tarefa limitada.
Invoke-RestMethod "$base/api/integrations/tarefas/$($tarefa.idTarefa)/retomar?maximoPaginas=50" -Method Post
Invoke-RestMethod "$base/api/integrations/tarefas/$($tarefa.idTarefa)/cancelar" -Method Post
```

Para descobrir os IDs das tarefas preparadas automaticamente no pgAdmin:

```sql
SELECT id, fonte, status, ate_esgotar, paginas, recebidos, processados, erros, mensagem
FROM import_job ORDER BY criado_em DESC;
```

O progresso mostra páginas concluídas, recebidos, processados, erros individuais,
`ultimaCarga` e mensagem. Cada página cria uma `carga_dados`; consulte-a para auditoria.
Totais e cursor são atualizados ao concluir a página, não a cada registro.

| Status | Significado |
| --- | --- |
| PENDENTE | Gravada na fila |
| EM_EXECUCAO | Disponível para o worker ou processando uma página |
| CONCLUIDA | Fim do resultado/recurso selecionado, sem erros por registro |
| CONCLUIDA_COM_ERROS | Fim do resultado/recurso, com registros que falharam |
| FALHA | Página incompleta; checkpoint anterior preservado |
| LIMITE_ATINGIDO | Há continuação, mas o orçamento de páginas terminou |
| CANCELADA | Processamento cancelado; dados já gravados são preservados |

No modo limitado manual, `maximoPaginas` aceita 1 a 100000 e tem padrão 1. O tamanho máximo da página é 100.
Retomar aceita falha/limite; tarefas limitadas recebem orçamento adicional, até 100000 páginas.
Tarefas completas mantêm seu modo sem orçamento máximo ao retomar. Uma tarefa ativa ou retomável por fonte é permitida; no DataJud,
por tribunal. Outra tarefa para o mesmo escopo retorna 409: retome ou cancele a anterior.
Cancelar durante uma página retorna 409; tente após seu checkpoint.

## Incremental

```powershell
$incremental = Invoke-RestMethod "$base/api/integrations/datajud/import?incremental=true" `
    -Method Post -ContentType 'application/json' `
    -Body '{"tribunal":"TJSP","tamanhoPagina":100,"maximoPaginas":50}'
```

Sem histórico, coleta a consulta completa até o orçamento de páginas. Retome as tarefas
limitadas até concluir a carga inicial. A próxima execução usa o limite superior da
última coleta completa sem erros do tribunal, com sobreposição de cinco minutos.
O filtro usa `@timestamp` de indexação e fixa o limite superior ao enfileirar.
Cargas com falha/limite/erros, buscas por processo e janelas manuais não avançam o marco.
Não há agendamento implícito: dispare o endpoint quando necessário.

Uma importação normal também aceita `desde` e `ate` como timestamps ISO-8601.
`incremental=true` não aceita esses campos nem `numeroProcesso`. SciELO/BDTD mantêm
os filtros de datas próprios. Demais fontes não têm atualização incremental genérica.
A sobreposição não garante detectar mudanças com timestamps retroativos; reconciliações
periódicas por janela explícita podem ser necessárias conforme a fonte.

## Worker e recuperação

Um worker por instância processa uma página por vez. `FOR UPDATE SKIP LOCKED` impede
que instâncias processem simultaneamente a mesma tarefa. O lock fica na linha da tarefa
durante a página; HTTP, auditoria e registros rodam fora da transação do checkpoint.
Reserve conexões no pool para o lock e para as gravações dos registros.

Um reinício libera o lock e permite repetir a página pendente. Identificadores naturais
evitam duplicar dados de negócio; a auditoria pode conter mais de uma tentativa.
Os contadores refletem páginas com checkpoint. Erros individuais não interrompem os
demais registros; retomar não repete registros isolados de páginas já concluídas.
Mensagens são limitadas para não acumular todos os erros em memória.

STJ/STJ_PRECEDENTES paginam arquivos locais. A tarefa reutiliza referências aos brutos
validados do catálogo/JSON/CSV, evitando baixar o arquivo inteiro novamente por offset
e mantendo o mesmo snapshot durante a carga. Uma nova tarefa obtém um snapshot novo.
O coletor STJ interpreta JSON/ZIP por streaming e materializa apenas os registros da página.
ZIPs podem expandir além de 32 MiB sem carregar todo o conteúdo expandido na memória.
O limite de 32 MiB por download permanece. CSVs de precedentes ainda são interpretados por página. Outras fontes podem alterar resultados ou expirar tokens.
Cursor repetido/inválido causa falha, em vez de um loop infinito.

| Variável | Padrão | Uso |
| --- | --- | --- |
| WIKILAW_IMPORT_WORKER_ENABLED | true | false permite só enfileirar/consultar |
| WIKILAW_IMPORT_POLL_DELAY | 1000 | Intervalo em milissegundos após cada ciclo |

O bootstrap documental somente enfileira tarefas; não espera downloads na inicialização.
Os testes desativam worker/bootstrap/atualização periódica e usam respostas controladas, sem coletar o acervo
externo no GitHub Actions.

## Contrato e desempenho

Clientes administrativos que esperavam `idCarga`, `ids` ou `proxima` no POST passam a
ler `idTarefa` e consultar o progresso. A continuação fica no backend; para várias
páginas documentais, informe `?maximoPaginas=N`. Pesquisa e detalhes mantêm seu contrato.

Esta entrega libera a requisição HTTP, evita downloads repetidos no STJ e reduz o volume
das cargas incrementais. O ganho total depende da fonte e do banco. Batching, otimização
de SQL e maior paralelismo exigem medição com dados reais.

## Validação em PostgreSQL isolado

Na pasta `backend`, `./mvnw verify` executa a suíte e gera o relatório de cobertura.
`ImportJobIntegrationTest` cobre o intervalo persistido, bloqueio de tarefas ativas
ou com falha, watermark incremental, validação dos pedidos de todas as fontes e
atualização de um documento existente sem duplicação. `LegalRefreshSchedulerTest`
cobre desativação, ausência de chave DataJud e isolamento das falhas por fonte.
As respostas HTTP das fontes são controladas nesses testes.

Os testes normais usam H2 e desativam os coletores automáticos. Para validar também
Flyway, JPA, JSONB, a fila e seu índice de exclusão em um PostgreSQL **descartável**:

```powershell
.\mvnw.cmd '-Dtest=ImportJobIntegrationTest' `
    '-Dwikilaw.test.postgres-url=jdbc:postgresql://127.0.0.1:55444/postgres' `
    '-Dwikilaw.test.postgres-user=wikilaw_test' test
```

Esses testes alteram os dados do banco indicado; nunca aponte para um banco em uso.
