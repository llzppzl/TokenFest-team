# TokenFest

TokenFest is a self-hosted gateway for LLM APIs, built for small teams that share one company account at OpenAI, Anthropic or DeepSeek. Today the whole team usually shares one API key, so nobody knows who spent what until the bill comes, and one runaway script can use up the month's budget. With TokenFest, each member gets their own TokenFest key, and apps and AI tools only change their base URL. The gateway forwards each request to the right provider, switches to a backup provider when one fails, records the tokens and cost of every request, enforces a monthly budget and rate limit per key, and alerts the team before a budget runs out. Admins see where the money goes on a dashboard; members see their own usage.

## Team

| Member | GitHub Username | Role |
|---|---|---|
| Phoebe L | [@llzppzl](https://github.com/llzppzl) | Gateway core (proxy, routing, providers), project setup, integration |
| Xiaohan Wang | [@vickyzz0512](https://github.com/vickyzz0512) | Backend services (keys, auth, limits, usage, alerts, stats, export) and backend unit tests |
| Mingzhu Wan | [@Mingzhu666](https://github.com/Mingzhu666) | Frontend dashboard, Docker deployment, frontend tests |

## Repository Structure

The layout follows the architecture in our Phase 1 SRS ([docs/SRS.docx](docs/SRS.docx), [docs/architecture.md](docs/architecture.md)): a Spring Boot gateway, a React dashboard and a PostgreSQL database. The backend is split by feature, and inside each feature into controller (HTTP), service (business logic) and repository (data access) classes.

```
TokenFest-team/
  backend/                           Spring Boot 4 gateway (Java 21, Maven)
    src/main/java/com/tokenfest/gateway/
      proxy/        OpenAI/Anthropic endpoints, routing, fallback, upstream client, SSE streaming
      provider/     provider management API and cached provider catalog
      keys/         member API keys (create, check, regenerate)
      auth/         JWT login, security rules, login throttling
      limits/       monthly budget and rate limit checks
      usage/        token and cost calculation, background usage writer
      alerts/       budget alerts by webhook and email
      stats/        dashboard numbers and request log
      export/       CSV export
      settings/     settings stored in the database
      user/         API for team members (own data only)
      maintenance/  nightly cleanup of old request logs
      crypto/       AES-GCM encryption of provider keys
      domain/       JPA entities
      repository/   Spring Data repositories
      config/, web/ app settings, shared error handling
    src/main/resources/
      application*.yml               settings (default, H2, Docker profiles)
      db/migration/                  Flyway database schema
    src/test/java/                   JUnit 5 unit tests
  frontend/                          React + TypeScript dashboard (Vite, Tailwind CSS)
    src/pages/                       Login, Overview, Requests, Providers, Keys, Settings, member dashboard
    src/components/                  layout, charts, export button, UI components
    src/api.ts, src/types.ts         API client and shared types
    src/*.test.ts                    Vitest unit tests
  docs/                              SRS, architecture, API, database, deployment, user guide, wireframes
  docker-compose.yml                 full stack: PostgreSQL + backend + dashboard (nginx)
```

## Setup and Run

**Prerequisites:** JDK 21 or newer, Node.js 22.12 or newer. Optional: Docker (for PostgreSQL or the full stack). An API key from at least one LLM provider (OpenAI, Anthropic, DeepSeek or any OpenAI-compatible server) to send real requests.

```bash
# Clone
git clone https://github.com/llzppzl/TokenFest-team.git
cd TokenFest-team

# Backend settings (keep the same encryption key between runs)
export TOKENFEST_ENCRYPTION_KEY=$(openssl rand -base64 32)
export TOKENFEST_ADMIN_PASSWORD=change-me

# Run the backend on http://localhost:8080 with an H2 file database (no Docker needed)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# In a second terminal: run the dashboard on http://localhost:5173
cd frontend
npm install
npm run dev

# Run tests
cd backend && ./mvnw test
cd frontend && npx vitest run
```

**Full stack with Docker** (PostgreSQL + backend + dashboard on http://localhost:8080):

```bash
cp .env.example .env    # set TOKENFEST_ENCRYPTION_KEY and TOKENFEST_ADMIN_PASSWORD in .env
docker compose up -d --build
```

All settings are environment variables; [.env.example](.env.example) lists them. Never commit `.env` or real API keys. Provider keys are entered in the dashboard and stored encrypted.

## Progress So Far

| Functional Requirement (from Phase 1) | Status | Notes |
|---|---|---|
| FR1: Admin login to the dashboard | Complete | JWT login; 10 attempts per minute per address |
| FR2: Add and manage providers | Complete | API key (encrypted), base URL, models and prices; connection test |
| FR3: Create and manage member API keys with budget and rate limit | Complete | Key shown once, only a SHA-256 hash is stored; regenerate and disable |
| FR4: Forward OpenAI- and Anthropic-format requests, including streaming | Complete | `/v1/chat/completions`, `/v1/responses`, `/v1/embeddings`, `/v1/models`, `/v1/messages` |
| FR5: Fall back to another provider when one fails | Complete | On 408, 429, 5xx, timeouts and connection errors; failing providers cool down for 30 s |
| FR6: Block requests over the rate limit or budget | Complete | Returns 429 with a clear message; budget is checked before forwarding |
| FR7: Record tokens, cost and latency for each request | Complete | Includes Anthropic prompt-cache prices; streams that end early are estimated |
| FR8: Alerts at 50%, 80% and 100% of a budget | Complete | Webhook (Slack) and email; each alert is sent once per key and month |
| FR9: Usage charts and request log on the dashboard | Complete | Cost by key, model or provider; request log with bodies |
| FR10: Members see only their own usage | Complete | Member dashboard; members log in with their TokenFest key |
| FR11: Export monthly usage as CSV | Complete | Admin and member export |

**Working end to end:** an admin logs in, adds a provider with its models and prices, and creates a key for a team member. The member points the OpenAI SDK (or Claude Code) at `http://localhost:8080/v1` with that key and sends a chat request. The gateway checks the key, budget and rate limit, forwards the request to the provider, returns the answer, and the request appears in the dashboard's request log with its tokens and cost. We checked this flow on the current `main` with the H2 profile and a mock provider.

## Testing So Far

- **Backend:** 21 JUnit 5 unit tests in 7 classes, covering cost calculation (`CostCalculator`, `Money`, `Usage`), budget periods (`BudgetPeriod`), the concurrency limiter, date ranges for stats, and CSV escaping (`CsvExporter`). All 21 pass (`./mvnw test`).
- **Frontend:** 6 Vitest unit tests for the date and session utilities. All 6 pass (`npx vitest run`).
- Not covered yet: the proxy, routing and fallback (`proxy/`), and the REST controllers. These need integration tests with a fake provider (see Next Steps).

## Next Steps (Phase 3)

- Integration tests for the proxy: streaming, fallback and budgets against a fake provider (WireMock), plus security tests for the admin and member APIs.
- Frontend component tests for the main pages, and an `npm test` script.
- GitHub Actions CI that builds and tests the backend and frontend on every pull request.
- Load test (target from the SRS: under 50 ms added per request with 50 concurrent users).
- A one-week trial with real provider keys, then fixes from the results.

## Team Contributions

| Member | Contributions This Phase | Commits |
|---|---|---:|
| Phoebe L | Responsible for the gateway core: committed `proxy/`, `provider/`, `crypto/`, `config/`, `web/`, the Maven build and the application settings. Set up the repository and project docs (README, contributing guide, Git workflow, SRS, architecture). Reviewed and merged the team's pull requests and resolved the `pom.xml` merge conflict so the backend tests run with the full build. | 25 |
| Xiaohan Wang | Responsible for the backend services: committed `keys/`, `auth/`, `limits/`, `usage/`, `alerts/`, `stats/`, `export/`, `settings/`, `user/`, `maintenance/`, `domain/`, `repository/` and the database schema. Wrote the 21 backend unit tests (`CostCalculatorTest`, `MoneyTest`, `UsageTest`, `BudgetPeriodTest`, `ConcurrencyLimiterTest`, `DateRangeTest`, `CsvExporterTest`). | 15 |
| Mingzhu Wan | Responsible for the frontend and deployment: committed the Vite and TypeScript setup, API client and types, auth context, layout, all dashboard pages, charts and export, the Docker, nginx and project configuration, and the user guide, deployment guide and wireframes. Added Vitest and wrote the 6 frontend unit tests (`dates.test.ts`, `session.test.ts`). | 23 |

