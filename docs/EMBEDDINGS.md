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
