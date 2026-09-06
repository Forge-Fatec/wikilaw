# WikiLaw

**WikiLaw** é uma plataforma voltada à pesquisa e análise de informações jurídicas, desenvolvida como projeto acadêmico da FATEC São José dos Campos.

A plataforma tem como objetivo centralizar e facilitar a consulta de **jurisprudência, precedentes e doutrina**, oferecendo uma experiência de pesquisa mais acessível e eficiente para profissionais do meio jurídico.

Além da pesquisa, o WikiLaw contará com recursos de **análise utilizando Inteligência Artificial**, buscando agregar contexto e informações relevantes aos resultados encontrados. Dessa forma, a ferramenta pretende auxiliar profissionais como **juízes e advogados** na análise de informações e no processo de tomada de decisões.

A plataforma também contará com uma **dashboard de análise**, permitindo uma visualização mais estruturada das informações jurídicas disponíveis.

## Funcionalidades

* Pesquisa de jurisprudência;
* Pesquisa de precedentes;
* Pesquisa de doutrina;
* Análise dos resultados utilizando Inteligência Artificial;
* Auxílio na interpretação e análise das informações encontradas;
* Dashboard para análise e visualização de dados jurídicos.

## Tecnologias

### Backend

* Java
* Spring Boot
* PostgreSQL

### Frontend

* React
* TypeScript
* Vite

### Infraestrutura

* Docker
* Docker Compose

## Como executar

### Pré-requisitos

Antes de executar o projeto, é necessário ter instalado:

* Docker
* Docker Compose

### Subindo os serviços

Na raiz do projeto, onde está localizado o `docker-compose.yml`, execute:

```bash
docker compose up --build
```

O comando irá construir as imagens do projeto e iniciar todos os serviços definidos no Docker Compose.

Para executar os containers em segundo plano:

```bash
docker compose up --build -d
```

### Acessos

Após a inicialização dos containers:

| Serviço    | Endereço                |
| ---------- | ----------------------- |
| Frontend   | `http://localhost:3000` |
| Backend    | `http://localhost:8080` |
| PostgreSQL | `localhost:5432`        |

### Parar os serviços

Para parar os containers:

```bash
docker compose down
```

Para parar os containers e também remover os volumes do projeto:

```bash
docker compose down -v
```

> **Atenção:** o comando `docker compose down -v` remove o volume do PostgreSQL e, consequentemente, os dados armazenados nele.

## Equipe

| Membro                | Função          |
| --------------------- | --------------- |
| **Thor Lyndgaard**    | Scrum Master    |
| **Thomas Heindrich**  | Product Owner   |
| **William Honda**     | Desenvolvimento |
| **Guilherme Bezerra** | Desenvolvimento |
| **João Paulista**     | Desenvolvimento |

## Documentação

A documentação do projeto está disponível no repositório:

**WikiLaw Docs:**
https://github.com/Forge-Fatec/wikilaw-docs

## Repositório

O código-fonte do projeto está disponível no GitHub:

https://github.com/Forge-Fatec/wikilaw
