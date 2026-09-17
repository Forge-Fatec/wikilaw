# Integrações de jurisprudência, precedentes e doutrina

## O que está implementado

| Fonte | Coleta | Dados operacionais |
| --- | --- | --- |
| DataJud | API pública CNJ, TJSP/TJRJ/TJMG | Processos, instâncias, classes, assuntos e movimentos (implementação anterior preservada) |
| TJDFT | POST na API JurisDF | Decisões, ementas, relatores, órgão, datas e inteiro teor **somente quando efetivamente recebido** |
| STJ | CKAN + recurso JSON de espelhos de acórdãos | Acórdãos, ementas, relator, órgão, datas e texto da decisão |
| STJ_PRECEDENTES | CKAN + Temas.csv e Processos.csv | Tipo/número do precedente, questão, tese, situação e processos relacionados |
| BDJUR | DSpace REST/Discovery | Título, autores, resumo/descrição, tipo, assuntos, data e link |
| BDTD | OAI-PMH, ListRecords/oai_dc | Títulos, autores, resumos, assuntos, datas e links, quando o servidor permitir a coleta |
| SCIELO | ArticleMeta | Artigos, autores, resumos, revista/ISSN, palavras-chave, idioma e DOI quando recebido |

A BDTD devolveu uma página de verificação de navegador na validação real. O coletor registra a resposta como HTML e finaliza a carga com FALHA. O parser XML, as proteções contra XXE e a paginação foram testados sem depender da disponibilidade externa. Não foi contornada a verificação.

DataJud não fornece inteiro teor de jurisprudência. Um acórdão não é automaticamente precedente vinculante. O dataset de precedentes do STJ também contém controvérsias: consultar sempre `tipo_precedente`, `situacao` e a fonte original.

BDJur inclui sumários de livros, não necessariamente a obra completa. BDTD e SciELO são multidisciplinares: o enquadramento como doutrina é uma categoria de pesquisa, não uma classificação editorial garantida. Não são baixados PDFs ou textos integrais acadêmicos nesta implementação.

## Arquitetura e rastreabilidade

Cada adaptador fica isolado em `integration/tjdft`, `integration/stj`, `integration/bdjur`, `integration/bdtd` ou `integration/scielo`. A infraestrutura comum fica em `integration/documents`.

1. O cliente consulta apenas os hosts oficiais autorizados.
2. Cada resposta completa é salva em `registro_bruto.payload_texto`, em transação independente.
3. JSON válido também é salvo em JSONB; CSV/XML/HTML permanecem em texto.
4. O adaptador transforma cada item em `NormalizedDocument`.
5. `DocumentRecordProcessor` persiste cada registro em sua própria transação.
6. `carga_dados` registra status, quantidades e falhas.

O payload original é preservado, inclusive respostas de erro HTTP. A resposta é limitada a 32 MiB e a 60 segundos; downloads interrompidos/incompletos não são apresentados como payloads completos. Há até duas novas tentativas para HTTP 502/503/504, auditadas separadamente. HTTP 429 e outros erros não são repetidos automaticamente. SciELO recebe no máximo uma nova chamada a cada 400 ms por instância do backend.

A chave única `(id_fonte, identificador_externo)` impede duplicação. Reimportar atualiza o registro e sua referência ao bruto mais recente; cargas/brutos anteriores permanecem para auditoria. Requisições simultâneas para a mesma chave podem gerar um erro de concorrência em uma carga, nunca duplicação silenciosa.

Registros explicitamente retirados (BDJur/BDTD) ou marcados como sigilosos (TJDFT) não entram na consulta pública; se já existirem, ficam inativos. A ocorrência aparece como erro individual da carga. O bruto é dado administrativo e não possui endpoint público de consulta.

### Banco: evolução incremental do modelo

V4 acrescenta:
- `decisao_judicial`;
- `precedente`;
- `precedente_processo`;
- `documento_doutrinario`;
- `vw_documentos_pesquisa`;
- cadastro das fontes e dos tribunais STJ/TJDFT.

V5 acrescenta o estado `ativo` e filtra a visão de leitura.

O modelo de referência foi implementado incrementalmente, não copiado integralmente. Nesta entrega autores, relatores, palavras-chave, órgão e periódico são campos textuais pesquisáveis. Cadastros globais de pessoas/instituições/periódicos e relações N:N ainda não são deduplicados: nomes iguais não comprovam identidade. Campos adicionais permanecem em `metadados` e no bruto.

O `Processos.csv` do STJ fornece números de registro internos, nem sempre número CNJ. Por isso `precedente_processo` guarda `numero_registro`, descrição, relator e indicador de leading case, vinculados pelo `sequencialPrecedente`. Não são inventados processos CNJ. Linhas sem identificadores suficientes ficam no bruto e geram `avisos` no resumo da importação.

Datas parciais como `2010` ou `20081200` permanecem em `data_original`; `data_publicacao` fica nula, sem inventar dia/mês. O campo STJ `decisao` é separado de `inteiro_teor`. No TJDFT, `possuiInteiroTeor=true` pode vir junto com “Inteiro Teor indisponível”: nesse caso não afirmamos possuir o texto.

## Executar

No diretório do repositório:

```powershell
docker compose up -d --build
```

O banco usa as migrations Flyway, com Hibernate em `validate`. Não execute o SQL de referência por cima de um banco já migrado.

- Swagger: http://localhost:8080/swagger-ui/index.html
- PostgreSQL do Compose: host `localhost`, porta padrão `5433`, banco/usuário/senha definidos no `.env`.
- PostgreSQL nativo em `5432` é outra instância. Confirme a conexão selecionada no pgAdmin.

As novas fontes públicas não exigiram chave nas consultas realizadas. DataJud continua usando `DATAJUD_API_KEY`. Não há novas credenciais obrigatórias. Nunca versionar `.env`.

As portas do Compose são publicadas apenas em `127.0.0.1`. Os endpoints de importação são administrativos de desenvolvimento, ainda sem autenticação/autorização de produção. Não exponha o backend por proxy/túnel público sem controles de acesso e rate limiting.

## Importar uma página

Endpoint:

```text
POST /api/integrations/documentos/{fonte}/import
```

Fontes: `TJDFT`, `STJ`, `STJ_PRECEDENTES`, `BDJUR`, `BDTD`, `SCIELO` (maiúsculas).

Cada chamada importa **uma página limitada**: padrão 10 itens, máximo 100. O resumo retorna `idCarga`, `status`, `recebidos`, `processados`, `erros`, `mensagemErro`, `ids`, `avisos` e `proxima`. HTTP 200 significa que a execução foi registrada; verifique o `status` da carga para saber se a fonte foi importada. Parâmetros inválidos/não suportados retornam HTTP 400.

Os contadores referem-se aos registros selecionados nesta página. Um recurso bruto do STJ ou uma página OAI pode conter mais registros que o limite de normalização.

Exemplo TJDFT:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/integrations/documentos/TJDFT/import' -Method Post -ContentType 'application/json' -Body '{"termo":"dano moral","pagina":0,"tamanhoPagina":5}'
```

Demais corpos para o Swagger (enviar somente os campos necessários):

| Fonte | Corpo |
| --- | --- |
| STJ | `{"dataset":"espelhos-de-acordaos-corte-especial","offset":0,"tamanhoPagina":5}` |
| STJ_PRECEDENTES | `{"offset":0,"tamanhoPagina":5}` |
| BDJUR | `{"termo":"direito constitucional","pagina":0,"tamanhoPagina":5}` |
| SCIELO | `{"issn":"1808-2432","offset":0,"tamanhoPagina":5}` |
| BDTD | `{"termo":"direito","offset":0,"tamanhoPagina":5}` |

DataJud mantém o endpoint anterior:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/integrations/datajud/import' -Method Post -ContentType 'application/json' -Body '{"tribunal":"TJSP","numeroProcesso":"40017037420268260360","tamanhoPagina":1,"maximoPaginas":1}'
```

Troque o tribunal por `TJRJ` ou `TJMG` e use um número correspondente ou remova `numeroProcesso`. Consultas amplas podem demorar ou atingir timeout.

### Paginação e filtros

- **TJDFT/BDJur:** repetir o termo/tamanho e usar `proxima.pagina` (inicia em zero).
- **STJ:** usar `proxima.offset` e `proxima.recursoId`, mantendo o dataset e tamanho. Sem recurso explícito, seleciona o JSON de nome mais recente; não baixa todo o histórico ZIP. Para outro período/órgão, consultar o [catálogo de acórdãos](https://dadosabertos.web.stj.jus.br/dataset/?q=espelhos-de-acordaos) e informar dataset/recurso.
- **STJ_PRECEDENTES:** repetir com `proxima.offset` e `proxima.recursoId`; o coletor combina os CSVs completos antes de selecionar os temas. Os arquivos podem mudar entre chamadas: reimporte para atualizar, não trate offset como snapshot imutável.
- **SciELO:** usar `proxima.offset`; padrão ISSN `1808-2432` (Revista Direito GV). Aceita outro ISSN e `desde`/`ate` no formato YYYY-MM-DD. Esses filtros seguem a semântica do ArticleMeta. Não aceita `termo` de busca livre.
- **BDTD:** filtro local literal por `termo` (padrão “direito”) nos metadados. Não equivale a classificador jurídico nem a busca textual remota. Pode haver página com zero itens e continuação. Usar `proxima.offset`/`proxima.resumptionToken`. Dentro da mesma página OAI, o offset evita perder itens quando o limite local é menor. Ao receber um novo token, não repetir `desde`, `ate` ou `conjunto`; o protocolo não permite combinar esses seletores com o token. O token pode expirar.
- `proxima=null` significa fim do recurso/resultado consultado, não fim de todo o acervo da fonte.

Não alterar o termo/tamanho/ISSN/dataset no meio da paginação. As coletas são manuais, sem agendamento e sem garantia de atualização em tempo real.

## Ler os dados

Endpoints paginados (página zero, tamanho até 100):

```text
GET /api/documentos/decisoes?fonte=TJDFT&termo=dano&tamanho=10
GET /api/documentos/precedentes?fonte=STJ&tamanho=10
GET /api/documentos/doutrina?fonte=SCIELO&tamanho=10
GET /api/documentos/doutrina?fonte=BDJUR&termo=direito
```

Retornam título, autores/relator, resumo/ementa, datas e link, sem exigir leitura do JSON bruto.

Detalhes e processos vinculados:

```text
GET /api/documentos/decisoes/{id}
GET /api/documentos/precedentes/{id}
GET /api/documentos/doutrina/{id}
GET /api/documentos/precedentes/{id}/processos
```

O ID é local e deve ser usado com a categoria correta. No detalhe, `metadados` preserva os campos adicionais da fonte como texto JSON; não renderize HTML externo sem sanitização no futuro frontend.

No Query Tool do pgAdmin:

```sql
SELECT categoria, fonte, titulo, autores_ou_relator,
       resumo_ou_ementa, data_publicacao, data_original, url_original
FROM vw_documentos_pesquisa
ORDER BY categoria, fonte, id DESC
LIMIT 50;
```

Mais consultas prontas: [consultas_fontes.sql](consultas_fontes.sql). Em contraste com `registro_bruto`, essa visão já traz campos próprios para leitura.

## Testes e validação

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd package
```

Os testes usam H2/mocks e não dependem da internet. Cobrem os coletores e mapeamentos, CSV multilinha, OAI/XML seguro, datas parciais, filtros/paginação, payload bruto antes da normalização, falhas individuais/HTTP, idempotência, retirada de documentos e consultas.

A validação manual usa PostgreSQL real para testar Flyway, JSONB, chaves estrangeiras, consultas e importações externas. Consulte [VALIDACAO_FONTES.md](VALIDACAO_FONTES.md) para o resultado desta execução e os bloqueios de ambiente. Isso não significa que todo o acervo foi carregado.

## Referências oficiais consultadas

- [API pública DataJud](https://datajud-wiki.cnj.jus.br/api-publica/)
- [TJDFT: API e documentação](https://www.tjdft.jus.br/transparencia/tecnologia-da-informacao-e-comunicacao/dados-abertos/webservice-ou-api)
- [STJ: acórdãos da Corte Especial](https://dadosabertos.web.stj.jus.br/dataset/espelhos-de-acordaos-corte-especial)
- [STJ: precedentes, CSVs e chave de relacionamento](https://dadosabertos.web.stj.jus.br/dataset/precedentes-qualificados)
- [BDJur: API DSpace](https://bdjur.stj.jus.br/server/api)
- [BDTD: serviço OAI](https://bdtd.ibict.br/vufind/oai)
- [SciELO: cliente oficial ArticleMeta](https://github.com/scieloorg/articlemetaapi/blob/master/articlemeta/client.py)

Fora do escopo: frontend, autenticação de produção, scraping de páginas protegidas, download de obras completas, DW, OpenSearch e IA.
