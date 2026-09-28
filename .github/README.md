### CI flow

On pull requests and pushes to main or develop:

1. Three jobs run in parallel:
   - Backend tests using Java 25 and Maven.
   - Frontend lint and production build using Node.js 22.
   - Playwright tests across Chromium, Firefox, and WebKit.

2. Playwright automatically starts the Vite development server and uploads its HTML
   report.

3. After all checks pass, Docker Compose is validated and the backend/frontend images are
   built.

4. SonarQube remains a local-only analysis tool and is not run by GitHub Actions.

### Project integration

The React frontend currently uses local in-memory legal documents; its API search is
still a placeholder. I replaced the external Playwright example with tests covering the
actual application:

- Page title, main heading, and initial results.
- Document-type filtering behavior.

Relevant changes:

- frontend/playwright.config.ts:14
- frontend/tests/example.spec.ts:1
- frontend/package.json:7

The Spring backend tests use the existing H2 test configuration, so CI does not need
PostgreSQL or external APIs for unit/integration testing. Docker validation separately
confirms that the production PostgreSQL/backend/frontend images can be assembled.

Validation completed successfully:

- Backend Maven verification on Java 25
- Frontend lint and production build
- 6 Playwright executions passed across three browsers
- Docker Compose configuration validation
- Backend and frontend Docker image builds
- git diff --check passed
