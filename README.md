# WikiLaw

**WikiLaw** is a platform focused on the research and analysis of legal information, developed as an academic project at FATEC São José dos Campos.

The platform aims to centralize and simplify access to **case law, legal precedents, and legal doctrine**, providing a more accessible and efficient research experience for legal professionals.

In addition to legal research, WikiLaw will provide **Artificial Intelligence-powered analysis** to add context and relevant insights to search results. This feature is intended to assist professionals such as **judges and lawyers** in analyzing legal information and supporting their decision-making process.

The platform will also include an **analysis dashboard**, providing a structured way to visualize and analyze legal information.

## Features

* Case law research;
* Legal precedent research;
* Legal doctrine research;
* Artificial Intelligence-powered analysis of search results;
* Assistance in interpreting and analyzing retrieved information;
* Dashboard for legal data analysis and visualization.

## Technologies

### Backend

* Java
* Spring Boot
* PostgreSQL

### Frontend

* React
* TypeScript
* Vite

### Infrastructure

* Docker
* Docker Compose

## How to Run

### Prerequisites

Before running the project, make sure you have installed:

* Docker
* Docker Compose

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

O backend também possui coletores isolados para TJDFT, STJ (acórdãos e precedentes), BDJur, BDTD/OAI-PMH e SciELO/ArticleMeta. Eles reutilizam as cargas e os registros brutos, sem alterar o contrato do DataJud.

Consulte [o guia das integrações](docs/INTEGRACOES.md) para exemplos no Swagger/PowerShell, paginação, consultas legíveis no pgAdmin e limitações de cada fonte. A BDTD está implementada e testada com XML de teste, mas a coleta real está bloqueada pela verificação de navegador do serviço; isso é registrado como falha, não como importação bem-sucedida.

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

## Documentation

The project's documentation is available in the following repository:

**WikiLaw Docs:**
https://github.com/Forge-Fatec/wikilaw-docs
