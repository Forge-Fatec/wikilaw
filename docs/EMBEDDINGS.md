# Geração de embeddings do acervo

> SCRUM-206: base da busca por similaridade com IA. Esta etapa gera e guarda os vetores;
> a comparação com a descrição do caso fica na busca semântica.

## Fluxo

```text
documento importado
  └─ SemanticContentService.prepare      → trecho_semantico (texto_para_embedding)
                                            documento_semantico.status_embedding = PENDENTE
SemanticEmbeddingWorker (@Scheduled)
  1. reserva documentos (SKIP LOCKED)    → status_embedding = PROCESSANDO
  2. envia os trechos à Voyage AI        → fora de transação, em lotes
  3. grava os vetores                    → embedding_trecho, status_embedding = PRONTO
```

| `status_embedding` | Significado |
| --- | --- |
| `PENDENTE` | Trechos novos ou alterados, aguardando vetores |
| `PROCESSANDO` | Reservado por um worker; volta para a fila após `processing-timeout` |
| `PRONTO` | Todos os trechos têm vetor do modelo em `modelo_embedding` |
| `ERRO` | Falha registrada em `erro_embedding`; nova tentativa após `retry-backoff`, até `max-attempts` |

- Quando a preparação recria os trechos, o `ON DELETE CASCADE` remove os vetores antigos e o documento volta a `PENDENTE`.
- Trocar `WIKILAW_EMBEDDING_MODEL` regera todo o acervo automaticamente.
- HTTP 429 (limite de requisições) não consome tentativas.
- Os vetores são normalizados; o índice HNSW usa distância de cosseno (`vector_cosine_ops`).

## Configuração (Voyage AI)

1. Crie uma chave em <https://dashboard.voyageai.com>. Ela fica **somente** no `.env`, que é ignorado pelo Git.
2. No `.env`:

```dotenv
WIKILAW_EMBEDDING_ENABLED=true
WIKILAW_EMBEDDING_API_KEY=<sua-chave>
```

3. Recrie o backend:

```bash
docker compose up -d --build postgres backend
```

O Compose já define `base-url=https://api.voyageai.com`, `model=voyage-4`, `dimensions=1024`
e `send-input-type=true` (trechos como `document`, consultas como `query`).
Sem cartão cadastrado, a conta gratuita tem limite baixo de requisições por minuto; a primeira carga é lenta,
mas os documentos voltam para a fila sozinhos.

O cliente usa `POST /v1/embeddings`, então Ollama, LM Studio ou OpenAI também funcionam trocando
`BASE_URL`, `MODEL` e `SEND_INPUT_TYPE=false`, desde que o modelo produza 1024 dimensões
(a coluna `embedding_trecho.vetor` é `vector(1024)`; o backend não sobe com outro valor).

## Acompanhar

```bash
docker compose logs -f backend | grep -i embedding
```

```sql
select status_embedding, modelo_embedding, count(*)
from documento_semantico where status = 'PRONTO'
group by 1, 2;

select count(*) from embedding_trecho;

select id_documento_semantico, tentativas_embedding, erro_embedding
from documento_semantico where status_embedding = 'ERRO';
```

## Recuperação por similaridade

`SemanticVectorRetrievalService.recuperar(vetor, categorias, limite)` devolve os documentos mais próximos de um vetor de consulta:

1. `EmbeddingTrechoRepository.findSimilares` ordena por `vetor <=> :consulta` (distância de cosseno), o que usa o índice HNSW.
   Só entram vetores do modelo atual (`embedding_trecho.modelo`) de documentos ativos (`documento_semantico.status = 'PRONTO'`) das categorias pedidas.
2. Na mesma transação, `set_config(..., true)` aplica `hnsw.ef_search` e `hnsw.iterative_scan`, para que os filtros não reduzam a quantidade de resultados.
3. Buscam-se `limite × candidate-multiplier` trechos, agrupados por documento: vale a similaridade do melhor trecho
   (`1 - distância`, de 0 a 1), com desempate pela quantidade de trechos semelhantes. O melhor trecho volta como justificativa.

| Propriedade | Padrão | Uso |
| --- | --- | --- |
| `WIKILAW_SEMANTIC_SEARCH_EF_SEARCH` | `100` | Candidatos examinados pelo HNSW |
| `WIKILAW_SEMANTIC_SEARCH_ITERATIVE_SCAN` | `relaxed_order` | `off`, `strict_order` ou `relaxed_order` |
| `WIKILAW_SEMANTIC_SEARCH_CANDIDATE_MULTIPLIER` | `5` | Trechos buscados por documento pedido |
| `WIKILAW_SEMANTIC_SEARCH_MAX_RESULTS` | `50` | Limite máximo por consulta |

Conferir no banco os vizinhos de um trecho já indexado:

```sql
with alvo as (select vetor from embedding_trecho order by id_trecho_semantico limit 1)
select t.secao, left(t.texto, 100), round((1 - (e.vetor <=> a.vetor))::numeric, 3) as similaridade
from alvo a, embedding_trecho e join trecho_semantico t using (id_trecho_semantico)
order by e.vetor <=> a.vetor limit 5;
```

## Representação semântica do caso pesquisado

`SemanticCaseRepresentationService.representar(descricao)` transforma o texto digitado pelo usuário no vetor de consulta:

1. Aplica a mesma normalização do acervo (`LegalTextNormalizer`: remove HTML, espaços e caracteres de controle).
2. Valida o tamanho: descrições vazias ou com menos de `min-characters` não carregam contexto suficiente; acima de
   `max-characters` são recusadas. Os dois casos lançam `SemanticQueryException`, sem chamar o provedor.
3. Gera o vetor com `embedQuery`, que envia `input_type=query` à Voyage AI, no mesmo espaço vetorial dos trechos (`document`).
4. Guarda o vetor em um cache LRU em memória (chave = modelo + texto normalizado). Repetir a pesquisa, trocar de
   página ou de filtro não gasta nova requisição, o que importa com o limite por minuto da conta gratuita.

O resultado (`texto`, `vetor`, `modelo`) é a entrada de `SemanticVectorRetrievalService.recuperar`.

| Propriedade | Padrão | Uso |
| --- | --- | --- |
| `WIKILAW_SEMANTIC_QUERY_MIN_CHARACTERS` | `20` | Tamanho mínimo da descrição |
| `WIKILAW_SEMANTIC_QUERY_MAX_CHARACTERS` | `20000` | Tamanho máximo (cerca de 5 mil tokens; o `voyage-4` aceita 32 mil) |
| `WIKILAW_SEMANTIC_QUERY_CACHE_SIZE` | `200` | Descrições guardadas no cache; `0` desliga |

Exemplo validado com o acervo real: a descrição *"Comprei um imóvel e moro nele há mais de quinze anos de forma
contínua e sem oposição de ninguém..."*, sem a palavra "usucapião", recupera precedentes de usucapião e de compromisso
de compra e venda de imóvel.

## Fatos, fundamentos e pedidos no contexto da busca

O caso pesquisado pode vir em partes (`SemanticCaseQuery`): `fatos`, `fundamentos`, `pedidos` e, opcionalmente, a
`descricao` livre. `SemanticCaseContextComposer` monta um único texto rotulado, no mesmo estilo dos trechos do acervo:

```text
Fatos:
O banco descontou parcelas de um empréstimo que eu nunca contratei da minha aposentadoria.

Fundamentos juridicos:
Código de Defesa do Consumidor, falha na prestação do serviço.

Pedidos:
Devolução em dobro dos valores descontados e indenização por dano moral.
```

- A ordem é fixa (fatos, fundamentos, pedidos, descrição); partes vazias são ignoradas e textos repetidos entram uma vez.
- Cada parte passa pela normalização do acervo. O mínimo de caracteres vale para o conteúdo informado (sem os rótulos)
  e o máximo para o texto final.
- Um único vetor representa o caso inteiro, então a similaridade considera os três elementos juntos, e não só a parte
  mais longa. Pesquisas equivalentes (mesmo conteúdo, espaços diferentes) compartilham o cache.
- `representar(String)` continua aceitando a descrição livre.

Exemplo validado com o acervo real: o caso acima recuperou em primeiro lugar o IRDR do TJMA sobre empréstimos
consignados (similaridade 0,588), seguido de precedentes sobre legitimidade do banco e devolução de tarifas.
