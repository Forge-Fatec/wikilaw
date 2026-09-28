# WikiLaw

**WikiLaw** is a platform focused on the research and analysis of legal information, developed as an academic project at FATEC São José dos Campos.

The platform aims to centralize and simplify access to **case law, legal precedents, and legal doctrine**, providing a more accessible and efficient research experience for legal professionals.

In addition to legal research, WikiLaw will provide **Artificial Intelligence-powered analysis** to add context and relevant insights to search results. This feature is intended to assist professionals such as **judges and lawyers** in analyzing legal information and supporting their decision-making process.

The platform will also include an **analysis dashboard**, providing a structured way to visualize and analyze legal information.

## Features

- Case law research;
- Legal precedent research;
- Legal doctrine research;
- Artificial Intelligence-powered analysis of search results;
- Assistance in interpreting and analyzing retrieved information;
- Dashboard for legal data analysis and visualization.

## Technologies

### Backend

- Java
- Spring Boot
- PostgreSQL

### Frontend

- React
- TypeScript
- Vite

### Infrastructure

- Docker
- Docker Compose

## How to Run

### Prerequisites

Before running the project, make sure you have installed:

- Docker
- Docker Compose

The backend targets Java 25.
the application build, tests, and SonarQube analysis use Docker images with JDK 25.

### Starting the Services

From the project root directory, where the `docker-compose.yml` file is located, run:

```bash
docker compose up --build
```

This command will build the project images and start all services defined in the Docker Compose configuration.

To run the containers in the background:

```bash
docker compose up --build -d
```

### Service Access

After the containers have started:

| Service    | Address                 |
| ---------- | ----------------------- |
| Frontend   | `http://localhost:3000` |
| Backend    | `http://localhost:8080` |
| PostgreSQL | `localhost:5432`        |
| SonarQube  | `http://localhost:9000` |

### Code Analysis with SonarQube

The backend is configured with SonarScanner for Maven and JaCoCo. The scanner
runs the tests, generates the XML coverage report, and sends the analysis to the
local SonarQube instance.

Create the local environment file and add the project token:

```bash
cp .env.example .env
```

```dotenv
SONAR_TOKEN=your_project_token
```

The `.env` file is ignored by Git. You can also omit the file and export
`SONAR_TOKEN` directly in the shell environment. Run the analysis with:

```bash
./sonar-analysis.sh
```

The script starts the local SonarQube dependency when necessary and runs Maven,
the tests, JaCoCo, and SonarScanner in a JDK 25 container. The dashboard remains
available at `http://localhost:9000`.
| PostgreSQL | `localhost:5433`        |

### Stopping the Services

To stop the containers:

```bash
docker compose down
```

To stop the containers and remove the project's volumes:

```bash
docker compose down -v
```

> **Warning:** `docker compose down -v` removes the PostgreSQL volume and, consequently, all data stored in it.

## Integração DataJud

A primeira integração operacional implementa o fluxo abaixo sem acoplar o formato externo às entidades JPA:

```text
DataJud/CNJ
  -> DataJudClient (HTTP e resposta original como texto)
  -> registro_bruto (payload_texto)
  -> DataJudParser / DTOs específicos
  -> registro_bruto (payload_jsonb, após validar o JSON)
  -> DataJudMapper (modelo normalizado independente da fonte)
  -> DataJudRecordProcessor (transação por processo)
  -> PostgreSQL
```

Cada página HTTP recebida gera um `registro_bruto`. Se a resposta for JSON válido, o mesmo conteúdo também é armazenado em `payload_jsonb`. Se o JSON estiver inválido, o texto original continua preservado para auditoria. Uma falha em um processo não desfaz os demais processos válidos da carga.

Os aliases inicialmente permitidos são `TJSP`, `TJRJ` e `TJMG`. Todos utilizam o mesmo client; endpoint, tribunal e metadados são selecionados por configuração, sem duplicação de integrações.

### Configuração

Copie o arquivo de exemplo e preencha os valores locais:

```bash
cp .env.example .env
```

No PowerShell:

```powershell
Copy-Item .env.example .env
```

Variáveis reconhecidas:

| Variável | Obrigatória | Finalidade |
| --- | --- | --- |
| `DATAJUD_API_KEY` | Sim, para importar | Chave pública vigente publicada pelo CNJ; nunca é registrada em logs |
| `DATAJUD_BASE_URL` | Não | Base oficial; útil para mocks e ambientes de teste |
| `DATAJUD_CONNECT_TIMEOUT` | Não | Timeout de conexão, padrão `5s` |
| `DATAJUD_READ_TIMEOUT` | Não | Timeout total da requisição, padrão `30s` |
| `DATAJUD_MAX_RETRIES` | Não | Retries após a primeira tentativa, padrão `2`; somente HTTP 429, 5xx e falhas de I/O |
| `DATAJUD_RETRY_DELAY` | Não | Espera base entre tentativas, padrão `500ms` |
| `POSTGRES_PASSWORD` | Sim | Senha local do PostgreSQL; o Compose falha cedo se ela não estiver definida |
| `POSTGRES_DB`, `POSTGRES_USER` | Não no Compose | Nome do banco e usuário locais |
| `POSTGRES_HOST_PORT` | Não | Porta do PostgreSQL exposta no Windows, padrão `5433`; evita conflito com uma instalação local na porta `5432` |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Sim fora do Compose | Conexão do backend quando executado diretamente |

A chave pública atual deve ser copiada da [documentação oficial de acesso do DataJud](https://datajud-wiki.cnj.jus.br/api-publica/acesso/). Ela não possui valor padrão no código nem no arquivo de exemplo.

### Executar e aplicar migrations

O projeto usa Flyway porque não havia ferramenta de migration na `main`. O Hibernate está em `ddl-auto=validate`: o Flyway cria/versiona o schema e o JPA apenas verifica sua compatibilidade.

```bash
docker compose up --build
```

As migrations são executadas automaticamente na inicialização do backend. Para executar os testes sem depender da API externa:

```bash
cd backend
./mvnw test
```

No Windows:

```powershell
Set-Location backend
.\mvnw.cmd test
```

### Disparar uma carga

O endpoint administrativo síncrono recebe somente parâmetros controlados e devolve um resumo:

```bash
curl -X POST http://localhost:8080/api/integrations/datajud/import \
  -H "Content-Type: application/json" \
  -d '{"tribunal":"TJSP","tamanhoPagina":1,"maximoPaginas":1}'
```

Também é possível consultar um número CNJ específico:

```json
{
  "tribunal": "TJMG",
  "numeroProcesso": "5009874-63.2022.8.13.0567",
  "tamanhoPagina": 10,
  "maximoPaginas": 1
}
```

`tamanhoPagina` aceita de 1 a 100 e `maximoPaginas`, de 1 a 50. O retorno contém `idCarga`, `fonte`, `tribunal`, `status`, `recebidos`, `processados`, `erros` e uma mensagem resumida. O endpoint nunca devolve o conjunto importado.

### Tabelas preenchidas e mapeamento

| DataJud | Banco operacional |
| --- | --- |
| página HTTP original | `registro_bruto.payload_texto` e `payload_jsonb` |
| execução | `carga_dados` |
| `tribunal` | `tribunal.sigla` |
| `numeroProcesso` | `processo.numero_cnj` |
| `_id` / `_source.id` | `processo_instancia.identificador_externo` |
| `grau`, `dataAjuizamento`, `nivelSigilo`, `sistema`, `formato` | `processo_instancia` |
| `orgaoJulgador` | `orgao_julgador` |
| `classe` | `classe_processual` |
| `assuntos` | `assunto_processual` e `processo_assunto` |
| `movimentos` | `movimento_processual` |
| `movimentos.complementosTabelados` | `movimento_processual.complementos_jsonb` |

As chaves naturais/externas impedem duplicidade de processo, instância, tribunal, órgão, classe e assunto. Em uma reimportação, assuntos e movimentos da instância são substituídos transacionalmente pelo retrato atual retornado pelo DataJud.

Para comprovar os registros diretamente no banco:

```bash
docker compose exec postgres psql -U wikilaw -d wikilaw -c \
  "select c.id_carga, c.status, c.quantidade_recebida, c.quantidade_processada, c.quantidade_erro from carga_dados c order by c.id_carga desc limit 5;"

docker compose exec postgres psql -U wikilaw -d wikilaw -c \
  "select p.numero_cnj, pi.grau, t.sigla, cp.nome as classe, count(distinct pa.id_assunto_processual) assuntos, count(distinct m.id_movimento) movimentos from processo p join processo_instancia pi on pi.id_processo=p.id_processo join tribunal t on t.id_tribunal=pi.id_tribunal left join classe_processual cp on cp.id_classe_processual=pi.id_classe_processual left join processo_assunto pa on pa.id_processo_instancia=pi.id_processo_instancia left join movimento_processual m on m.id_processo_instancia=pi.id_processo_instancia group by p.numero_cnj, pi.grau, t.sigla, cp.nome order by p.numero_cnj limit 20;"
```

### Compatibilidade com os dados reais

Uma consulta real feita durante a implementação confirmou os aliases e os campos documentados pelo CNJ. Ela também mostrou `dataAjuizamento` no formato compacto `yyyyMMddHHmmss` e movimentos com código/data, mas sem `nome`. Por isso o mapper aceita datas compactas e ISO-8601, e a migration permite `movimento_processual.nome` nulo. O payload bruto preserva qualquer campo desconhecido ou divergente.

A paginação segue o `search_after` ordenado por `@timestamp`, conforme a documentação do CNJ. Os limites do endpoint evitam uma coleta acidentalmente ilimitada. Não foi identificado limite numérico oficial de requisições; portanto não há retry infinito nem suposição de rate limit.

### Jurisprudência, precedentes e doutrina

Também está disponível a [integração Pangea/BNP](docs/PANGEA.md), com pesquisa pública e importação de precedentes, questões e teses.

O backend também possui coletores isolados para TJDFT, STJ (acórdãos e precedentes), BDJur, BDTD/OAI-PMH e SciELO/ArticleMeta. Eles reutilizam as cargas e os registros brutos, sem alterar o contrato do DataJud.

Consulte [o guia das integrações](docs/INTEGRACOES.md) para exemplos no Swagger/PowerShell, paginação, consultas legíveis no pgAdmin e limitações de cada fonte. A BDTD está implementada e testada com XML de teste, mas a coleta real está bloqueada pela verificação de navegador do serviço; isso é registrado como falha, não como importação bem-sucedida.

### Padrão das buscas de documentos

A busca principal usa os endpoints `GET /api/documentos/decisoes`,
`GET /api/documentos/precedentes` e `GET /api/documentos/doutrina`.
Jurisprudência (`decisoes`) já utiliza a busca especializada por palavras-chave;
precedentes e doutrina ainda devem ser migrados para o mesmo padrão estrutural.

O componente compartilhado
`forge.wikilaw.backend.service.search.SearchTermProcessor` define o contrato de
processamento textual:

- converte o termo para minúsculas;
- separa letras e números com suporte a caracteres Unicode;
- remove conectivos comuns em português;
- elimina palavras repetidas preservando a ordem;
- escapa `%`, `_` e `\` antes do uso em consultas `LIKE`.

Novas buscas não devem duplicar essa lógica. O serviço especializado da
categoria deve injetar `SearchTermProcessor`, definir seus campos pesquisáveis,
filtros e ordenação, e expor uma consulta paginada para `DocumentQueryService`.
O padrão esperado é:

```text
Busca de documentos
├── DocumentQueryService
│   ├── JurisprudenciaQueryService.buscarPagina()
│   ├── PrecedenteQueryService.buscarPagina()
│   └── DoutrinaQueryService.buscarPagina()
└── SearchTermProcessor (compartilhado pelas três categorias)
```

Consulte [o guia de busca de documentos](docs/BUSCA_DOCUMENTOS.md) para os
campos de cada categoria, limitações atuais e a sequência recomendada de
implementação.

As migrations acrescentam `decisao_judicial`, `precedente`, `precedente_processo`, `documento_doutrinario` e a visão de leitura `vw_documentos_pesquisa`. Os endpoints administrativos são para desenvolvimento local; as portas do Compose ficam publicadas apenas em `127.0.0.1`.

ETL/Data Warehouse, OpenSearch, pgvector e IA permanecem fora desta entrega.

## Team

| Member                | Role          |
| --------------------- | ------------- |
| **Thor Lyndgaard**    | Scrum Master  |
| **Thomas Heindrich**  | Product Owner |
| **William Honda**     | Developer     |
| **Guilherme Bezerra** | Developer     |
| **João Paulista**     | Developer     |

# Product Backlog

| Rank | User Story | Priority | Estimate |
|:---:|---|:---:|:---:|
| 1 | As a user, I want to find case law, precedents, and legal doctrine related to the case presented so that I can learn about judicial understandings related to my situation. | High | 13 |
| 2 | As a user, I want to submit the description of my case to start searching for related legal content. | High | 3 |
| 3 | As a user, I want to view the results found by the search so that I can consult content related to my case. | High | 5 |
| 4 | As a user, I want to view a dashboard with indicators and charts related to my searched case so that I can understand the results found in a consolidated way. | High | 5 |
| 5 | As a user, I want to interact with dashboard elements so that I can dynamically filter the displayed indicators and results. | High | 3 |
| 6 | As a user, I want to receive an AI-assisted analysis of the case presented so that I can better understand the legal information related to my situation. | High | 13 |
| 7 | As a user, I want AI to consider the results obtained by the search when analyzing my case so that the analysis is connected to the information found by the system. | High | 8 |
| 8 | As a user, I want to understand which information and sources were used as the basis for the AI-generated analysis so that I can evaluate its conclusions. | High | 5 |
| 9 | As a user, I want to view the source of each result so that I know where the information presented by the system was obtained. | Medium | 5 |
| 10 | As a user, I want to access the original source of a result so that I can directly verify the content presented by the system. | Medium | 3 |
| 11 | As a user, I want to identify the court or authority responsible for the content found so that I can understand its origin. | Medium | 3 |
| 12 | As a user, I want to compare data related to my case with the court's overall landscape so that I can understand how my search results are positioned in relation to the broader set of decisions. | Medium | 3 |
| 13 | As a user, I want AI to interpret the context of my case so that it can find legally similar content even when it does not use exactly the same terms as my search. | Medium | 8 |
| 14 | As a user, I want to view results ordered by similarity, along with a percentage and an explanation of their similarity, so that I can understand which content is most closely related to my case. | Medium | 5 |
| 15 | As a user, I want to interact with the generated analysis so that I can obtain clarification and explore specific aspects of my case. | Medium | 5 |
| 16 | As a user, I want to keep track of relevant information related to my case so that I can identify possible changes or new elements that may impact its analysis. | Medium | 5 |
| 17 | As a user, I want to view results from the most recent to the oldest so that I can prioritize more current legal content. | Low | 3 |
| 18 | As a user, I want to view the case number related to the result, when available, so that I can identify it. | Low | 2 |
| 19 | As a user, I want to view a summary of the result so that I can understand its content before accessing the full source. | Low | 3 |
| 20 | As a user, I want to filter results by period, court, case law, precedent, or legal doctrine so that I can consult content published or decided within a specific time period. | Low | 3 |
| 21 | As a user, I want to upload documents related to my case in PDF or DOCX format so that I can provide additional information that can be used to search for similar content. | Low | 5 |
| 22 | As a user, I want to view the main facts and legal arguments identified by AI in the uploaded documents so that I can verify the information that will be used in the similarity search. | Low | 3 |
| 23 | As a user, I want to export my search results and information as a PDF so that I can consult or share them later. | Low | 3 |
---

# Definition of Ready (DoR)

A User Story will be considered **ready for development** when:

- It is described clearly and understandably;
- It has defined acceptance criteria;
- Its main business questions have been clarified;
- Its dependencies have been identified;
- It has been prioritized in the Product Backlog;
- It has been understood by the development team;
- It has an estimate defined by the team.

---

# Definition of Done (DoD)

A User Story will be considered **done** when:

- All acceptance criteria for the User Story have been met;
- The functionality has been implemented according to the expected behavior;
- The required tests have been performed and passed;
- There are no errors that prevent the functionality from working;
- The functionality has been integrated with the rest of the system without compromising existing features;
- The functionality has been integrated and is available on the `main` branch of the project's official repository;
- The User Story has been validated by the Product Owner according to the defined acceptance criteria.

## Documentation

The project's documentation is available in the following repository:

**WikiLaw Docs:**
https://github.com/Forge-Fatec/wikilaw-docs
