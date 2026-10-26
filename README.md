# TokenFest

A self-hosted gateway for LLM APIs. Each team member gets their own TokenFest key. Apps and AI tools only change their base URL. TokenFest forwards requests to OpenAI, Anthropic, DeepSeek or any OpenAI-compatible provider, switches to a backup when one fails, tracks tokens and cost, enforces budgets and rate limits, and alerts the team before money runs out.

The idea is based on the open-source project [TokenParty](https://github.com/tokenpartyhub/TokenParty). TokenFest is our own implementation in Java with Spring Boot.

## Features

- OpenAI-compatible (`/v1/chat/completions`, `/v1/models`) and Anthropic-compatible (`/v1/messages`) endpoints, including streaming
- One key per team member; provider keys are stored encrypted and never shown
- Routing by model and priority, with automatic fallback on 429, 5xx, timeouts and connection errors
- Token and cost tracking per request (Anthropic prompt-cache prices included)
- Monthly budget and rate limit per key
- Budget alerts at 50% / 80% / 100% by webhook (Slack) or email
- Admin dashboard: cost charts, request log, providers, keys, settings
- Team member dashboard: own spending, budget, requests
- CSV export per month

## Project structure

```
backend/             Spring Boot gateway (Java 21, Maven)
frontend/            React dashboard (TypeScript, Vite, Tailwind CSS) + nginx config
docs/                Requirements, architecture, database, API, wireframes, guides
docker-compose.yml   Full stack: PostgreSQL + backend + dashboard
```

## Requirements

- JDK 21 or newer (Maven comes with the wrapper `./mvnw`)
- Node.js 22.12 or newer
- Optional: Docker, for PostgreSQL or the full stack

## Run with Docker

```bash
cp .env.example .env
# In .env set TOKENFEST_ENCRYPTION_KEY (openssl rand -base64 32) and TOKENFEST_ADMIN_PASSWORD
docker compose up -d --build
```

Open http://localhost:8080 and log in as `admin`.

## Run without Docker (development)

Backend on http://localhost:8080, with an H2 file database (no Docker needed):

```bash
export TOKENFEST_ENCRYPTION_KEY=$(openssl rand -base64 32) TOKENFEST_ADMIN_PASSWORD=change-me
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
```

To use PostgreSQL instead, start it with `docker compose up -d postgres` and run `./mvnw spring-boot:run`.

Keep the same `TOKENFEST_ENCRYPTION_KEY` between runs, or saved provider keys can't be read.

Dashboard on http://localhost:5173 (forwards `/api` to the backend):

```bash
cd frontend
npm install
npm run dev
```

## Try it end to end

1. Log in to the dashboard as `admin`.
2. **Providers:** add a provider (for example OpenAI with your API key) and its models with prices.
3. **Keys:** create a key for a team member. The full key is shown once.
4. Point a tool at the gateway:

```python
from openai import OpenAI
client = OpenAI(base_url="http://localhost:8080/v1", api_key="tf-...")
print(client.chat.completions.create(model="gpt-4o", messages=[{"role": "user", "content": "hi"}]))
```

```bash
# Claude Code
export ANTHROPIC_BASE_URL=http://localhost:8080 ANTHROPIC_API_KEY=tf-...
```

5. **Overview** and **Requests** in the dashboard show the request, its tokens and its cost.

## Build

```bash
cd backend && ./mvnw package        # backend/target/gateway-*.jar
cd frontend && npm run build        # frontend/dist
```

## Documentation

| | |
|---|---|
| [User guide](docs/user-guide.md) | Admins and team members: setup, connecting tools, dashboards, alerts, errors |
| [Deployment](docs/deployment.md) | Docker, settings, HTTPS, backups, upgrades |
| [API](docs/api.md) | Proxy, auth, admin and user endpoints |
| [Architecture](docs/architecture.md) | Components, request flow, packages |
| [Database](docs/database.md) | Tables and decisions |
| [Wireframes](docs/wireframes.md), [SRS](docs/SRS.docx) | Design and requirements |

## Team workflow

- [WEEK1_TASK_DISTRIBUTION.md](WEEK1_TASK_DISTRIBUTION.md): who owns which module and what to do this week
- [CONTRIBUTING.md](CONTRIBUTING.md): rules for branches, commits and reviews
- [GIT_WORKFLOW.md](GIT_WORKFLOW.md): exact Git commands for each member

Tests are planned for a later week and are not part of this Week 1 baseline.

## Tech stack

Java 21, Spring Boot 4 (WebFlux, Security, Data JPA), Flyway, PostgreSQL, Caffeine, Bucket4j · React, TypeScript, Vite, Tailwind CSS, Recharts · Docker, nginx
