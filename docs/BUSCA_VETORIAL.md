# Busca por similaridade no servidor

O navegador envia a descrição e/ou o texto extraído do arquivo ao backend. O backend acessa o modelo pela rede interna do Compose. Todos os PCs que acessam o site compartilham os serviços e o banco do servidor, sem instalar Docker ou baixar modelos.

## Executar

1. Crie `.env` a partir de `.env.example` e defina `POSTGRES_PASSWORD`.
2. Execute `docker compose up -d --build postgres embeddings backend frontend`.
3. Abra `http://localhost:3000`. Na primeira execução, o serviço baixa o modelo; o cache permanece no volume `embedding_models`. O backend pode iniciar a importação antes de o modelo estar pronto.
4. Para outros PCs, configure `WIKILAW_BIND_ADDRESS=0.0.0.0`, recrie o frontend e acesse `http://IP-DO-SERVIDOR:3000`. A API usa o mesmo endereço do site. Modelo, banco e backend não precisam de portas públicas. O nginx não encaminha as rotas administrativas `/api/integrations/`; elas continuam disponíveis na porta local 8080 para administração.

O PostgreSQL permanece na versão 16, com a imagem `pgvector/pgvector:pg16-trixie`. O volume `postgres_data` é preservado; Flyway V15 acrescenta as tabelas e os índices da busca vetorial local, sem alterar os vetores de 1024 dimensões da integração externa. Instalações PostgreSQL fora do Compose precisam disponibilizar a extensão `vector` antes de aplicar as migrations. Não há geração de embeddings durante builds ou migrations.

If a development database already applied the branch-only `V11__embeddings_vetoriais.sql`, back it up and reconcile its Flyway history before updating. That version conflicted with develop's V11 and is now V15. Do not delete volumes or run Flyway repair blindly; databases that only ran develop's migrations can apply V15 normally.

| Variável | Padrão no Compose | Efeito |
| --- | --- | --- |
| `WIKILAW_VECTOR_ENABLED` | `true` | Consulta e worker vetorial |
| `WIKILAW_EMBEDDING_THREADS` | `2` | Threads de CPU do modelo |
| `WIKILAW_BIND_ADDRESS` | `127.0.0.1` | Interface pública do frontend |

Fora do Compose, a busca vetorial tem padrão desativado. Para ativar, configure `WIKILAW_VECTOR_ENABLED=true` e `WIKILAW_EMBEDDING_URL` com o endereço interno do serviço. `VITE_API_BASE_URL` é opcional: o padrão usa o endereço atual do site; Vite encaminha `/api` ao backend local durante desenvolvimento.

## Modelo e indexação

Usamos [`intfloat/multilingual-e5-small`](https://huggingface.co/intfloat/multilingual-e5-small), com português, 384 dimensões, licença MIT e revisão fixa `614241f622f53c4eeff9890bdc4f31cfecc418b3`. O PyTorch roda em CPU. Os prefixos `passage:` e `query:` identificam acervo e pesquisa. Entradas acima da janela do modelo são divididas em janelas sobrepostas e agregadas, sem descartar a parte final por truncamento.

O worker processa oito trechos preparados por lote. Vetores de mesmo hash e modelo são reutilizados. Alterações de conteúdo invalidam os trechos anteriores, removendo seus vetores pela chave estrangeira. Documentos inativos e vetores de hash/modelo obsoleto não entram na consulta.

A tabela `fila_embedding` mantém apenas as pendências, evitando varrer todos os trechos já indexados a cada lote. Triggers enfileiram trechos novos ou alterados. Uma versão diferente de modelo enfileira novamente o acervo uma vez. Reinícios preservam o trabalho concluído. Uma falha de inferência desfaz o lote e aguarda 30 segundos antes de tentar novamente. Locks `FOR UPDATE SKIP LOCKED` coordenam instâncias do backend. O serviço de inferência limita concorrência e serializa o modelo para controlar CPU e memória.

Documentos e vetores ficam no volume do banco; o cache do modelo contém apenas arquivos públicos do modelo. O arquivo do usuário e seus vetores de consulta não entram no acervo nem são persistidos. Os textos não são enviados a uma API de IA externa nem registrados nos logs do serviço.

## Pesquisa e limites

A inferência usa um scheduler próprio no backend. Uma chamada lenta ao modelo não ocupa o scheduler da importação e da preparação de textos.

Na tela, escolha **Similaridade** ou **Palavras-chave** e clique em **Pesquisar**, com descrição, anexo ou ambos. Cada trecho do arquivo participa; a descrição recebe peso próprio para não ser diluída por um anexo longo. Com similaridade habilitada, a abertura do site não baixa o acervo completo: os candidatos são recuperados somente ao pesquisar.

A consulta utiliza distância de cosseno e índice HNSW do [pgvector](https://github.com/pgvector/pgvector). O backend agrupa trechos do mesmo documento e combina o ranking vetorial com correspondências textuais em português por reciprocal rank fusion. Cada documento aparece uma vez, com um trecho relacionado para conferência. O score não é apresentado como porcentagem de acerto jurídico.

São recuperados até 200 documentos, ordenados por relevância. **Os filtros recortam esse conjunto de candidatos; não executam outra consulta global vetorial.** A tela informa esse limite. Indicadores e gráficos usam os IDs dos candidatos filtrados, sem refazer a pesquisa textual. O PDF preserva o conjunto, a ordem, o nome do anexo e o tipo da busca. Também é possível ordenar por data.

A indexação inicial pode demorar em um acervo grande, especialmente em CPU. A busca usa o que já está indexado e informa quando há trechos pendentes. Se não houver vetores ou o modelo falhar, apresenta uma mensagem e permite selecionar **Palavras-chave**, sem trocar o método silenciosamente. A qualidade precisa ser avaliada com exemplos representativos do acervo jurídico.

## API

- `GET /api/pesquisas/similaridade/status`: `habilitada`, `indexados`, `pendentes` (contagens de trechos).
- `POST /api/pesquisas/similaridade`: `{ "termo": "descrição opcional", "texto": "anexo opcional", "limite": 200 }`. Exige ao menos um texto útil; máximo de 300 caracteres de descrição, 100.000 de anexo e de 1 a 200 resultados. Retorna `itens: [{ documento, similaridade, trecho }]`, contagens e limite. Respostas usam `Cache-Control: no-store`.
- `POST /api/dashboard/resultados`: `{ "idsDecisoes": [1, 2], "resultado": null, "orgaoJulgador": null }`. Até 200 referências públicas; reutiliza as agregações para os resultados recuperados.
- O PDF aceita `modoPesquisa: "similaridade"` e `ordenacao: "relevancia"`, mantendo as exportações textuais anteriores.

O serviço interno expõe `/health` e `/embed`, com até 16 textos por lote. O backend valida versão, dimensionalidade e valores finitos. O identificador inclui a revisão do modelo e o algoritmo de janelas. Alterar modelo/procedimento exige versionar a indexação; outra dimensionalidade exige migração de esquema.

## Testes

`VectorSearchServiceTest` cobre configuração, índice vazio, texto completo e idempotência. `embeddings/tests` cobre contrato, validação e erros sem exposição de textos; a CI roda esses testes sem baixar o modelo.

`VectorPostgresIntegrationTest` usa PostgreSQL/pgvector e modelo reais para recuperar uma paráfrase em português, eliminar duplicatas, reindexar hashes antigos e excluir documentos retirados. É ignorado na suíte leve. Para executar, disponibilize um banco de teste dedicado com usuário/senha `postgres` e o serviço de inferência; configure `WIKILAW_VECTOR_TEST_JDBC_URL` e `WIKILAW_VECTOR_TEST_EMBEDDING_URL` e rode `./mvnw -Dtest=VectorPostgresIntegrationTest test`. O teste executa as migrations por padrão; `WIKILAW_VECTOR_TEST_FLYWAY=false` permite usar um banco de teste previamente migrado.
