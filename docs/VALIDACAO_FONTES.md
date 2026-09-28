# Validação das fontes — 15/09/2026

## Resultado

Implementação no repositório `C:\Users\Will_\OneDrive\Documentos\GitHub\bertoti\wikilaw`, branch `feature/database`, sem commit ou push automático.

- `mvnw.cmd package`: sucesso.
- 36 testes automatizados: zero falhas, zero erros.
- Spring Boot 4.1.1, Java 21; código compilado para Java 17 conforme o projeto.
- PostgreSQL 18.6 real: migrations V1–V5 aplicadas; JPA em `validate`.
- `docker compose config --quiet`: sucesso.
- Swagger: HTTP 200.
- Parâmetros de importação inválidos: HTTP 400.
- Paginação de consulta inválida: HTTP 400.
- Documento inexistente: HTTP 404.

## Amostra real persistida

| Categoria | Fonte | Registros distintos |
| --- | --- | ---: |
| Decisões | TJDFT | 2 |
| Decisões | STJ | 3 |
| Precedentes | STJ | 4 |
| Publicações | BDJur | 3 |
| Artigos | SciELO | 3 |

Também foram confirmados:
- 3 instâncias DataJud: uma de TJSP, uma de TJRJ e uma de TJMG;
- 142 movimentos processuais;
- 19 vínculos entre precedentes e registros processuais STJ;
- 34 respostas brutas preservadas;
- 22 cargas, incluindo os históricos de falha encontrados/corrigidos durante a validação.

O dataset STJ contém controvérsias e temas. Foram importadas três controvérsias e o Tema 1, este com tese recebida da fonte. Não foi inventada tese para as controvérsias que retornaram esse campo vazio.

As mesmas páginas foram reimportadas: os IDs das decisões, precedentes e publicações permaneceram iguais. A consulta de agrupamento por categoria/fonte/identificador externo não encontrou duplicações. Os vínculos STJ também não se multiplicaram.

Exemplos de títulos retornados pela consulta legível:
- SciELO: “A Emenda Constitucional 45 e a questão do acesso à justiça”.
- BDJur: “Estado constitucional de direito e a nova pirâmide jurídica” (sumário de livro, não íntegra).

## Observações reais de cada fonte

- **TJDFT:** a busca de teste retornou três registros, um explicitamente marcado `segredoJustica=true`. Apenas os dois públicos foram normalizados; a carga informou a exclusão como erro individual.
- **STJ acórdãos:** recurso selecionado `20260831.json`, ID `748ef76d-a248-434b-bb6a-deb444f70f73`, Corte Especial.
- **STJ precedentes:** os CSVs oficiais têm três linhas de processos sem chaves suficientes. O coletor preserva o CSV, informa o aviso e importa os temas/vínculos válidos. Recurso Temas.csv: `df29da13-7d6b-41ba-ad96-cd1a5bbd191c`.
- **BDJur:** busca DSpace real, com autores/descrições/links; certas obras disponibilizam somente o sumário.
- **SciELO:** artigos reais do ISSN `1808-2432`, com título, autoria e resumo. Datas incompletas foram mantidas como texto.
- **BDTD:** o servidor devolveu HTML “Verificando seu navegador”, não OAI-PMH. O registro bruto 9 preserva essa resposta e a carga 7 ficou em FALHA. Nenhum documento BDTD foi falsamente informado como importado.
- **DataJud/TJSP:** a busca ampla atingiu timeout; a consulta pelo número `40017037420268260360` concluiu normalmente. TJRJ e TJMG também concluíram.

## Ambiente e limites da validação

O Docker Desktop não disponibilizou o motor Linux. O log informou:
`engine linux/wsl failed to start: checking preconditions: Virtual Machine Platform not enabled`.
A consulta ao estado do recurso do Windows exigiu elevação, indisponível nesta execução. Não houve alteração desse recurso, reinicialização do computador, reset do Docker ou remoção de volume.

Para testar sem afetar o PostgreSQL nativo existente (5432) ou o volume Docker (5433), foi criada uma instância temporária isolada, acessível somente por loopback:
- banco `wikilaw_verificacao`;
- PostgreSQL `127.0.0.1:55434`;
- backend de teste `127.0.0.1:18080`.

**Essas amostras não foram inseridas no banco habitual do Docker.** A instância e o backend temporários foram encerrados após os testes; não dependem de manter a conversa aberta. Os arquivos temporários foram preservados para diagnóstico, fora do repositório:
`C:\Users\Will_\AppData\Local\Temp\wikilaw-sources-verification-2e2f5419837c4803b9aec938d67c12b9`.

Após corrigir o motor Docker, executar `docker compose up -d --build` no repositório e usar os exemplos de [INTEGRACOES.md](INTEGRACOES.md) para importar no banco do projeto. As migrations são aplicadas automaticamente.

O Compose não pôde ser executado de ponta a ponta nesta validação; a execução real comprovada foi no PostgreSQL nativo isolado. A BDTD precisa de acesso OAI-PMH liberado pela fonte/rede antes de ser considerada validada de ponta a ponta.

## Arquivos desta expansão

- Novos adaptadores em `backend/src/main/java/forge/wikilaw/backend/integration/{documents,stj,tjdft,bdjur,bdtd,scielo}`.
- Novas entidades: `DocumentoBase`, `DecisaoJudicial`, `Precedente`, `PrecedenteProcesso`, `DocumentoDoutrinario`, com repositories.
- Novos services: `DocumentImportService`, `DocumentRecordProcessor`, `DocumentQueryService`.
- Novos controllers: `DocumentIntegrationController`, `DocumentQueryController`.
- Migrations V4/V5 e três classes novas de testes.
- Alterados `RegistroBrutoService` (suporte a formatos), `pom.xml` (CSV/HTML), `docker-compose.yml` (portas locais) e `README.md`.
- Documentação e consultas SQL nesta pasta.

Alterações anteriores de DataJud e a remoção preexistente de `database/init.sql` foram preservadas. Não houve implementação de frontend.
