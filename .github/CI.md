# CI flow

Workflow: [`workflows/ci.yml`](workflows/ci.yml). Full documentation, with diagrams and FAQ (in Portuguese): [`docs/CI.md`](../docs/CI.md).

Runs on every pull request, on pushes to `main` and `develop`, and manually (`workflow_dispatch`). A new push cancels the previous run on the same branch.

| Job | What it does |
| --- | --- |
| Embedding service contract tests | Python 3.12 + pytest on `embeddings/`, with a stub encoder (no model download) |
| Backend unit tests | Java 25 + Maven: `./mvnw --batch-mode verify` (JUnit on H2, JaCoCo report) |
| Frontend checks | Node 22: `npm ci`, `npm run lint`, `npm run build` |
| Playwright (chromium, firefox, webkit) | E2E tests with a mocked API, inside `mcr.microsoft.com/playwright`; report uploaded for 7 days. Failures do not block the pipeline yet (`continue-on-error`) |
| Build Docker images | After backend, frontend and Playwright: `docker compose config` and `docker compose build backend frontend` |

That is 5 jobs and 7 checks on the PR (one Playwright check per browser).

Notes:

- CI needs no secrets: DataJud, Gemini and the other external APIs are mocked in the tests.
- Keep the Playwright image tag in sync with `@playwright/test` in `frontend/package-lock.json`.
- SonarQube runs locally only, not in GitHub Actions.
