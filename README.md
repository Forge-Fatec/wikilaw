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
| PostgreSQL | `localhost:5432`        |

### Periodic legal data refresh

The backend checks each source every minute and queues a new import 24 hours after
its last job finishes. DataJud uses an incremental window; document sources replay
their supported collections and update existing records by natural key. Active or
failed resumable jobs keep their checkpoints and prevent competing imports.
Set `WIKILAW_REFRESH_INTERVAL=7d` to change the interval or
`WIKILAW_REFRESH_ENABLED=false` to disable periodic refresh independently of startup
imports. See [the import guide](docs/IMPORTACAO_ASSINCRONA.md) for configuration,
progress monitoring and failure recovery.

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

## Open Source

WikiLaw is an open-source project developed with the goal of making legal research and analysis more accessible.

The project's source code is publicly available, allowing developers, students, and other interested contributors to study how it works, propose improvements, and contribute to its evolution.

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
| 24 | As a user, I want clearer dashboard charts and indicators so that I can interpret legal research results more easily. | High | 5 |
| 25 | As a user, I want dashboard indicators and charts to remain consistent with the applied filters so that I can trust the information displayed. | High | 3 |
| 26 | As a user, I want searches and dashboard requests to respond faster so that I can analyze legal information without unnecessary delays. | High | 8 |
| 27 | As a user, I want the system to remain responsive during periodic legal data imports so that I can continue my research without interruptions. | Medium | 5 |
| 28 | As a maintainer, I want automated build and test validation so that defective changes are identified before integration. | High | 5 |
| 29 | As a maintainer, I want reliable automated regression tests so that updates do not compromise existing functionalities. | High | 5 |
| 30 | As a maintainer, I want a repeatable deployment and rollback process so that new versions can be released safely. | High | 5 |

# Sprint Backlog

## Sprint 1

| Rank | User Story | Priority | Story Points |
|------|------------|----------|--------------|
| 1 | As a user, I want to find case law, legal precedents, and legal doctrine related to my case so that I can understand judicial interpretations relevant to my situation. | High | 13 |
| 2 | As a user, I want to submit a description of my case to search for related legal content. | High | 3 |
| 3 | As a user, I want to view the search results so that I can explore legal content related to my case. | High | 5 |
| 4 | As a user, I want to view the source of each result so that I can identify where the information displayed by the system was obtained. | Medium | 5 |
| 5 | As a user, I want to access the original source of a result so that I can directly verify the content displayed by the system. | Medium | 3 |
| 8 | As a user, I want to identify the court or judicial body responsible for the retrieved content so that I can understand its origin. | Medium | 3 |
| 6 | As a user, I want to sort search results from newest to oldest so that I can prioritize more recent legal content. | Low | 3 |
| 7 | As a user, I want to view the case number associated with a result, when available, so that I can identify it. | Low | 2 |
| 9 | As a user, I want to preview a summary of each result so that I can understand its content before accessing the full source. | Low | 3 |
| 10 | As a user, I want to filter results by date range, court, case law, precedent, or legal doctrine so that I can explore content published or decided within a specific period. | Low | 3 |

## Sprint 2

| Rank | User Story | Priority | Story Points |
|----|------------|----------|--------------|
| 11 | As a user, I want to view a dashboard with indicators and charts related to my researched case so that I can understand the results in a consolidated way. | High | 5 |
| 12 | As a user, I want to interact with dashboard elements to dynamically filter the displayed indicators and results. | High | 3 |
| 13 | As a user, I want to compare my case data with the court's overall statistics so that I can understand how my research results relate to the broader set of decisions. | Medium | 3 |
| 14 | As a user, I want AI to interpret the context of my case to find legally similar content, even when different terms are used in the documents. | Medium | 8 |
| 15 | As a user, I want to view results ranked by similarity, including a similarity percentage and an explanation, so that I can understand which content is most relevant to my case. | Medium | 5 |
| 17 | As a user, I want to view the main facts and legal arguments identified by AI in the submitted documents so that I can verify the information used for similarity searches. | Low | 3 |
| 18 | As a user, I want to export my research results and information as a PDF so that I can review or share them later. | Low | 3 |
| 19 | As a user, I want to log into the system with a role-based access profile (Administrator or Regular User), allowing administrators to create user accounts and manage their roles and permissions. | Low | 5 |

## Sprint 3

| Rank | User Story | Priority | Story Points |
|----|------------|----------|--------------|
| 1 | As a user, I want to receive an AI-assisted analysis of my case so that I can better understand the legal information related to my situation. | High | 13 |
| 2 | As a user, I want AI to consider the results obtained by the search when analyzing my case so that the analysis is connected to the information found by the system. | High | 8 |
| 3 | As a user, I want to understand which information and sources were used as the basis for the AI-generated analysis so that I can evaluate its conclusions. | High | 5 |
| 4 | As a user, I want to interact with the generated analysis so that I can obtain clarification and explore specific aspects of my case. | Medium | 8 |
| 5 | As a user, I want to keep track of relevant information related to my case so that I can identify possible changes or new elements that may impact its analysis. | Medium | 8 |
| 6 | As a user, I want an improved dashboard so that I can visualize and understand legal information more easily. | Medium | 3 |
| 7 | As a user, I want improved search functionalities so that I can find relevant legal information more efficiently. | Medium | 3 |
| 8 | As a user, I want my account and personal information to be protected by appropriate access permissions so that I can use the system securely. | Medium | 3 |
| 9 | As a user, I want to clear all applied search filters with a single button so that I can easily start a new search. | Low | 1 |
| 10 | As a user, I want to switch between light and dark mode so that I can customize the interface according to my preferences. | Low | 1 |

## Sprint 4

**Sprint Goal:** Improve dashboard usability, backend performance, software quality, and deployment readiness without expanding the functional scope of WikiLaw.

| Rank | User Story | Priority | Story Points |
|----|------------|----------|--------------|
| 1 | As a user, I want dashboard charts to have clearer labels, legends, and layouts so that I can interpret the displayed information more easily. | High | 3 |
| 2 | As a user, I want dashboard indicators to present clear loading, empty, and error states so that I can understand the current state of the analysis. | Medium | 2 |
| 3 | As a user, I want dashboard charts, indicators, and results to remain synchronized when filters change so that I can trust the displayed data. | High | 3 |
| 4 | As a user, I want faster legal searches so that I can retrieve relevant results without unnecessary delays. | High | 5 |
| 5 | As a user, I want dashboard data to load faster so that I can analyze search results efficiently. | High | 3 |
| 6 | As a user, I want to continue searching while ETL jobs are running so that periodic imports do not interrupt my research. | Medium | 5 |
| 7 | As a maintainer, I want GitHub Actions to validate backend and frontend builds and tests on pull requests so that integration failures are detected early. | High | 3 |
| 8 | As a maintainer, I want required CI checks to prevent merging changes that fail validation so that the main branch remains stable. | High | 2 |
| 9 | As a maintainer, I want additional unit and integration tests for critical backend flows so that regressions are detected automatically. | High | 3 |
| 10 | As a maintainer, I want end-to-end regression tests for search, dashboard, and authentication so that critical user flows remain functional. | High | 2 |
| 11 | As a maintainer, I want versioned Docker images and environment configuration so that releases can be reproduced across environments. | High | 3 |
| 12 | As a maintainer, I want a documented and verified manual deployment and rollback procedure for the three-server architecture so that releases can be performed safely. | High | 2 |

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
