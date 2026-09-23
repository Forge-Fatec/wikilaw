# Busca de documentos jurídicos

## Visão geral

A tela principal pesquisa três categorias de documentos:

| Categoria na interface | Categoria na API | Endpoint |
| --- | --- | --- |
| Jurisprudência | `decisoes` | `GET /api/documentos/decisoes` |
| Precedentes | `precedentes` | `GET /api/documentos/precedentes` |
| Doutrina | `doutrina` | `GET /api/documentos/doutrina` |

O frontend faz as três requisições em paralelo, converte as respostas para um
formato comum e concatena os resultados. Atualmente, cada requisição solicita
até 50 documentos. A paginação visual, os filtros de categoria, tribunal e data
são aplicados no navegador depois desse carregamento.

## Comportamento atual

### Jurisprudência

`GET /api/documentos/decisoes` reutiliza a mesma lógica especializada de
`GET /api/jurisprudencias`.

A consulta:

- considera somente decisões ativas;
- usa o componente compartilhado `SearchTermProcessor` para normalizar,
  tokenizar e escapar o termo;
- retorna uma decisão quando qualquer palavra relevante aparece em qualquer
  campo pesquisável.

Campos pesquisados:

- título;
- ementa;
- decisão;
- número do processo;
- relator;
- órgão julgador.

Filtros opcionais:

- `fonte`;
- `tribunal`;
- `dataDe`;
- `dataAte`;
- `pagina`;
- `tamanho`.

Os resultados são ordenados pela data de julgamento mais recente e, como
desempate, pelo identificador mais recente.

Exemplo:

```http
GET /api/documentos/decisoes?termo=dano%20moral&fonte=TJDFT&tribunal=TJDFT&dataDe=2025-01-01&dataAte=2025-12-31&pagina=0&tamanho=20
```

O endpoint `GET /api/jurisprudencias` continua disponível por compatibilidade e
possui um formato de resposta específico. Os dois endpoints compartilham a mesma
consulta interna, evitando diferenças na seleção das decisões.

### Precedentes

`GET /api/documentos/precedentes` ainda utiliza a busca textual genérica.

Ela procura a frase completa, sem diferenciar maiúsculas de minúsculas, em:

- título;
- questão jurídica;
- tese;
- situação.

Também considera somente documentos ativos e permite filtrar por `fonte`.

Exemplo: o termo `dano moral consumidor` somente encontra um precedente se essa
sequência completa estiver contida em pelo menos um dos campos pesquisados.

### Doutrina

`GET /api/documentos/doutrina` também utiliza a busca textual genérica pela frase
completa.

Campos pesquisados:

- título;
- resumo;
- autores;
- palavras-chave.

A consulta considera somente documentos ativos e permite filtrar por `fonte`.

## Limitações atuais

- Precedentes e doutrina não possuem a mesma tokenização usada para
  jurisprudência.
- A combinação de resultados não possui ranking global de relevância.
- A busca não implementa stemming, tolerância a erros de digitação, remoção de
  acentos ou busca semântica.
- A tela solicita todas as categorias, inclusive as desmarcadas pelo usuário.
- O frontend aplica tribunal e datas somente sobre os documentos previamente
  carregados. Como cada categoria está limitada a 50 itens, resultados válidos
  podem ficar fora desse conjunto.
- A paginação exibida pelo frontend é local e não representa a paginação global
  do banco de dados.

## Arquitetura recomendada

O endpoint público unificado deve continuar sendo `/api/documentos/{categoria}`.
O serviço coordenador deve delegar a consulta a uma implementação especializada
por categoria:

```text
DocumentQueryService
├── JurisprudenciaQueryService.buscarPagina()
├── PrecedenteQueryService.buscarPagina()
└── DoutrinaQueryService.buscarPagina()
```

As três implementações devem reutilizar um componente comum responsável por:

- normalização do termo;
- separação em palavras-chave;
- remoção de conectivos;
- eliminação de palavras repetidas;
- escape seguro de padrões SQL `LIKE`.

O componente `forge.wikilaw.backend.service.search.SearchTermProcessor` já
implementa normalização, tokenização, remoção de conectivos e escape de
`LIKE`. Os serviços novos devem injetá-lo em vez de copiar essas regras.

Cada serviço especializado permanece responsável pelos campos, filtros e
ordenação específicos de sua categoria, inclusive pelo filtro de documentos
ativos e pela construção dos predicados JPA.

### Busca especializada de precedentes

Campos recomendados:

- título;
- questão jurídica;
- tese;
- situação;
- número do tema;
- tipo do precedente.

Filtros recomendados:

- fonte;
- tribunal;
- data de julgamento;
- situação;
- tipo do precedente.

A ordenação padrão deve priorizar a data de julgamento mais recente e usar o
identificador como desempate.

### Busca especializada de doutrina

Campos recomendados:

- título;
- resumo;
- autores;
- palavras-chave;
- periódico;
- tipo do documento.

Filtros recomendados:

- fonte;
- autor;
- data de publicação;
- periódico;
- tipo do documento.

A ordenação padrão deve priorizar a data de publicação mais recente e usar o
identificador como desempate.

## Alterações recomendadas no frontend

O frontend deve:

1. requisitar somente as categorias selecionadas;
2. enviar `tribunal`, `dataDe` e `dataAte` ao backend quando aplicáveis;
3. utilizar a paginação retornada pela API em vez de paginar apenas os primeiros
   50 documentos no navegador;
4. preservar separadamente os totais e estados de paginação de cada categoria;
5. definir uma estratégia explícita para ordenar resultados de categorias
   diferentes.

## Sequência sugerida de implementação

1. [Concluído] Extrair a normalização e tokenização de jurisprudência para
   `SearchTermProcessor`.
2. Criar `PrecedenteQueryService` e seus testes de integração.
3. Criar `DoutrinaQueryService` e seus testes de integração.
4. Fazer `DocumentQueryService` delegar cada categoria ao serviço correspondente.
5. Atualizar o frontend para enviar filtros e respeitar a paginação do backend.
6. Definir e testar a ordenação combinada das três categorias.
