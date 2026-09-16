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

## Documentation

The project's documentation is available in the following repository:

**WikiLaw Docs:**
https://github.com/Forge-Fatec/wikilaw-docs
