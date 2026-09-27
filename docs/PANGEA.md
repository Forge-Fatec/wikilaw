# Pangea/BNP — integração de pesquisa

Adicionada em 17/09/2026. Usa a pesquisa pública do [Pangea/BNP](https://pangeabnp.pdpj.jus.br/), não a API autenticada de alimentação do BNP pelos tribunais.

## Importar e consultar

Após iniciar o backend (Flyway aplica V7 automaticamente):

```powershell
Invoke-RestMethod 'http://localhost:8080/api/integrations/documentos/PANGEA/import' -Method Post -ContentType 'application/json' -Body '{"termo":"direito","pagina":0,"tamanhoPagina":3}'
Invoke-RestMethod 'http://localhost:8080/api/documentos/precedentes?fonte=PANGEA'
```

Detalhes, incluindo a tese e os metadados:
`GET /api/documentos/precedentes/{id}`.

No pgAdmin:

```sql
SELECT p.id_precedente, p.tribunal_origem, p.tipo_precedente, p.numero_tema,
       p.questao_juridica, p.tese, p.situacao, p.url_original
FROM precedente p JOIN fonte_dados f USING (id_fonte)
WHERE f.sigla = 'PANGEA' AND p.ativo
ORDER BY p.id_precedente;
```

A página do WikiLaw inicia em 0, convertida para 1 no Pangea. Importação padrão: 10 itens, máximo 100. Use `proxima.pagina` mantendo termo e tamanho. Sem termo informado, pesquisa “direito”. A ordenação é textual; resultados podem mudar entre chamadas.

Nenhuma nova chave, cookie ou autenticação foi necessária nas requisições públicas verificadas. A integração não envia dados para cadastro no CNJ. Parâmetros não suportados pelo coletor, como `offset`, são rejeitados. Não há filtro específico por tribunal/espécie neste endpoint inicial: pesquisa em todas as opções disponibilizadas pelo catálogo.

## Contrato e mapeamento

A documentação da [PDPJ](https://docs.pdpj.jus.br/servicos-negociais/pangeabnp/) distingue o backend de pesquisa do serviço de integração do BNP. O contrato público foi confirmado no JavaScript distribuído pelo próprio portal, nos parâmetros e em consultas reais.

1. GET `/api/v1/parametros`: obtém `orgaos[].sigla` e `especies[].sigla`.
2. POST `/api/v1/precedentes`: corpo com `filtro`, `buscaGeral`, listas completas, `pagina`, `tamanhoPagina`, `ordenacao=Text` e `cancelados=false`.
3. Resposta: `resultados[]` e `total`. Não usar totais das agregações como total da pesquisa.
4. As duas respostas são auditadas em `registro_bruto` antes de normalizar.

| Campo externo | Destino |
| --- | --- |
| id | identificador_externo, único por fonte |
| orgao | tribunal_origem; id_tribunal somente quando já cadastrado |
| tipo / nr | tipo_precedente / numero_tema |
| questao | questao_juridica |
| tese | tese, texto sem tags HTML |
| situacao | situacao |
| processosParadigma, suspensoes, historico, ultimaAtualizacao | metadados e registro bruto |

`titulo` é composto de órgão, espécie e número. O link segue o formato de compartilhamento do portal: `/pesquisa?orgao=...&tipo=...&nr=...`.

Não confundir `ultimaAtualizacao` com data de publicação/julgamento. Não extrair tese de `highlight`, que pode ser apenas um trecho. Não converter rótulos como “Link Nota Técnica” em processos CNJ. Processos paradigma ficam nos metadados nesta entrega, sem fabricar vínculos em `precedente_processo`.

A pesquisa inclui notas técnicas e outras espécies: o tipo original é preservado, sem presumir que todos os resultados sejam vinculantes. `TJDF`, quando recebido, é associado ao tribunal local `TJDFT`; demais tribunais não cadastrados mantêm sua sigla em `tribunal_origem`, com FK nula, em vez de serem atribuídos ao STJ.

A mesma fonte e o mesmo ID atualizam o registro existente. Um mesmo tema coletado pelo STJ e pelo Pangea continua tendo duas origens rastreáveis; não há deduplicação semântica entre fontes.

## Limites

Sem download de PDFs, decisões adicionais ou coleta integral do acervo. Não houve mudanças de frontend, credenciais ou regras de autenticação. As proteções de HTTP, limite de tamanho, auditoria, transações e consultas locais reutilizam a infraestrutura existente.

Testes automatizados cobrem contrato, conversão de página, metadados, sanitização, indisponibilidade, idempotência e tribunal diferente do STJ. As requisições reais usam apenas o serviço público, sem contornar proteções.

## Validação realizada

Em 17/09/2026, `mvnw.cmd package -q` passou com 43 testes, sem falhas ou erros. A migration V6 e a validação JPA passaram em PostgreSQL 18 isolado. Foram importadas duas páginas de três itens; repetir a primeira preservou os mesmos IDs. O resultado foi seis registros distintos, com tese legível e órgão de origem preservado, incluindo associação correta de TJMG ao cadastro local.

Também foram verificados a consulta de detalhes, a enumeração PANGEA no Swagger e HTTP 400 para `offset` não suportado. Os serviços temporários foram encerrados após a validação. Esses registros de teste não foram inseridos no banco principal nem no Docker do usuário: para vê-los no seu ambiente, reinicie o backend atualizado e execute a importação acima.
