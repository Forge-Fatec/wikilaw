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

## Team

| Member                | Role          |
| --------------------- | ------------- |
| **Thor Lyndgaard**    | Scrum Master  |
| **Thomas Heindrich**  | Product Owner |
| **William Honda**     | Developer     |
| **Guilherme Bezerra** | Developer     |
| **João Paulista**     | Developer     |

# Product Backlog

### Sprint 1

| Rank | User Story | Priority | Estimate |
|:---:|---|:---:|:---:|
| 1 | As a user, I want to find case law, precedents, and legal doctrine related to the case presented so that I can learn about judicial understandings related to my situation. | High | 13 |
| 2 | As a user, I want to submit the description of my case to start searching for related legal content. | High | 3 |
| 3 | As a user, I want to view the results found by the search so that I can consult content related to my case. | High | 5 |
| 4 | As a user, I want to view the source of each result so that I know where the information presented by the system was obtained. | Medium | 5 |
| 5 | As a user, I want to access the original source of a result so that I can directly verify the content presented by the system. | Medium | 3 |
| 6 | As a user, I want to identify the court or authority responsible for the content found so that I can understand its origin. | Medium | 3 |
| 7 | As a user, I want to view results from the most recent to the oldest so that I can prioritize more current legal content. | Low | 3 |
| 8 | As a user, I want to view the case number related to the result, when available, so that I can identify it. | Low | 2 |
| 9 | As a user, I want to view a summary of the result so that I can understand its content before accessing the full source. | Low | 3 |
| 10 | As a user, I want to filter results by period, court, case law, precedent, or legal doctrine so that I can consult content published or decided within a specific time period. | Low | 3 |

### Sprint 2

| Rank | User Story | Priority | Estimate |
|:---:|---|:---:|:---:|
| 11 | As a user, I want to view a dashboard with indicators and charts related to my searched case so that I can understand the results found in a consolidated way. | High | 5 |
| 12 | As a user, I want to interact with dashboard elements so that I can dynamically filter the displayed indicators and results. | High | 3 |
| 13 | As a user, I want to compare data related to my case with the court's overall landscape so that I can understand how my search results are positioned in relation to the broader set of decisions. | Medium | 3 |
| 14 | As a user, I want AI to interpret the context of my case so that it can find legally similar content even when it does not use exactly the same terms as my search. | Medium | 8 |
| 15 | As a user, I want to view results ordered by similarity, along with a percentage and an explanation of their similarity, so that I can understand which content is most closely related to my case. | Medium | 5 |
| 16 | As a user, I want to upload documents related to my case in PDF or DOCX format so that I can provide additional information that can be used to search for similar content. | Low | 5 |
| 17 | As a user, I want to view the main facts and legal arguments identified by AI in the uploaded documents so that I can verify the information that will be used in the similarity search. | Low | 3 |
| 18 | As a user, I want to export my search results and information as a PDF so that I can consult or share them later. | Low | 3 |

### Sprint 3

| Rank | User Story | Priority | Estimate |
|:---:|---|:---:|:---:|
| 19 | As a user, I want to receive an AI-assisted analysis of the case presented so that I can better understand the legal information related to my situation. | High | 13 |
| 20 | As a user, I want AI to consider the results obtained by the search when analyzing my case so that the analysis is connected to the information found by the system. | High | 8 |
| 21 | As a user, I want to understand which information and sources were used as the basis for the AI-generated analysis so that I can evaluate its conclusions. | High | 5 |
| 22 | As a user, I want to interact with the generated analysis so that I can obtain clarification and explore specific aspects of my case. | Medium | 5 |
| 23 | As a user, I want to keep track of relevant information related to my case so that I can identify possible changes or new elements that may impact its analysis. | Medium | 5 |

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
