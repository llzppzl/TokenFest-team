# Week 1 Task Distribution

**Week 1 goal:** submit working end-to-end code: a request goes from an app, through the gateway, to a provider and back, and its usage shows up in the dashboard. Automated tests are planned for a later week.

## Where the code comes from

The Week 1 code was written by **Member 1 (Phoebe L)** before the team repository was set up. It is split into three packages by module (`Phoebe_L`, `Xiaohan_Wang`, `Mingzhu_Wan`). Each member adds their package from their own GitHub account in their own PR, and the commit message says the code was originally written by Phoebe L (see `GIT_WORKFLOW.md`). The project idea is based on [TokenParty](https://github.com/tokenpartyhub/TokenParty), as the README and SRS say.

Member 1's package goes to `main` first and contains the shared setup, but the backend only compiles once Member 2's package is merged too, and the dashboard comes with Member 3's. Merge in the order 1 → 2 → 3.

From Week 1 on, each member **owns** one module: they understand it, can explain it in the presentation, check that it works, document it, and commit their own improvements. Each person's commits show their own work only.

## Ownership summary

| Member | Module | Main directories / files | Week 1 work |
|---|---|---|---|
| **Member 1: Phoebe L** | Gateway core: proxy, routing, providers | `backend/.../proxy/`, `provider/`, `crypto/`, `config/`, `web/`, `GatewayApplication.java`, `backend/pom.xml`, `application*.yml` | Import the baseline; check the proxy end to end with a real provider; document the request flow and settings |
| **Member 2: Xiaohan Wang** | Keys, auth and accounting | `backend/.../keys/`, `auth/`, `limits/`, `usage/`, `alerts/`, `stats/`, `export/`, `settings/`, `user/`, `maintenance/`, `domain/`, `repository/`, `db/migration/` | Walk through every admin and user API; add a curl walkthrough to the API doc; fix the problems found |
| **Member 3: Mingzhu Wan** | Dashboard and deployment | `frontend/` (all), `docker-compose.yml`, `backend/Dockerfile`, `backend/.dockerignore`, `.env.example`, `docs/user-guide.md`, `docs/deployment.md`, `docs/wireframes.md` | Run the full stack with Docker; check every dashboard page; fix the lint warning; update the user and deployment guides |


## Week 1 functionality checklist

| # | Function | Owner | Included |
|---|---|---|---|
| 1 | OpenAI-compatible `/v1/chat/completions`, `/v1/models`, `/v1/responses`, `/v1/embeddings` | Member 1 | ✅ |
| 2 | Anthropic-compatible `/v1/messages` (+ `count_tokens`) | Member 1 | ✅ |
| 3 | Streaming (SSE) passthrough with usage tracking | Member 1 | ✅ |
| 4 | Routing by model and priority; fallback on 408/429/5xx/timeout; provider cool-down | Member 1 | ✅ |
| 5 | Provider management; provider keys encrypted (AES-GCM) | Member 1 | ✅ |
| 6 | Team member keys (`tf-...`, SHA-256 hash, shown once, regenerate) | Member 2 | ✅ |
| 7 | Admin login (JWT), key login for members, login throttling | Member 2 | ✅ |
| 8 | Monthly budget and rate limit per key | Member 2 | ✅ |
| 9 | Token and cost tracking; request log; daily totals | Member 2 | ✅ |
| 10 | Budget alerts (webhook, email) and settings | Member 2 | ✅ |
| 11 | Stats API, CSV export, member API, nightly log cleanup | Member 2 | ✅ |
| 12 | Database schema (Flyway), PostgreSQL + H2 | Member 2 | ✅ |
| 13 | Admin dashboard: Overview, Requests, Providers, Keys, Settings | Member 3 | ✅ |
| 14 | Member dashboard | Member 3 | ✅ |
| 15 | Docker Compose deployment (PostgreSQL + backend + nginx) | Member 3 | ✅ |

### Left out of Week 1 (planned for later weeks)

These exist in Member 1's private repository and will be brought in when the course asks for testing:

| Left out | What it was |
|---|---|
| `backend/src/test/` | Unit, integration, security and PostgreSQL (Testcontainers) tests; JaCoCo coverage gate |
| Frontend `*.test.ts(x)`, `setupTests.ts`, Vitest, Testing Library, jsdom | Dashboard tests |
| `e2e/` | End-to-end checks with the official OpenAI and Anthropic SDKs and a mock provider |
| `perf/` | JMeter load test |
| `.github/workflows/ci.yml` | CI that runs the tests |
| `docs/testing.md`, `docs/performance.md`, `docs/uat.md`, `docs/demo.md` | Test results, UAT plan, demo script |

---

## Member 1: Phoebe L, gateway core

**Files**

- `backend/src/main/java/com/tokenfest/gateway/proxy/`
- `backend/src/main/java/com/tokenfest/gateway/provider/`
- `backend/src/main/java/com/tokenfest/gateway/crypto/`
- `backend/src/main/java/com/tokenfest/gateway/config/`
- `backend/src/main/java/com/tokenfest/gateway/web/`
- `backend/src/main/java/com/tokenfest/gateway/GatewayApplication.java`
- Shared, owned by Member 1: `backend/pom.xml`, `backend/src/main/resources/application.yml`, `application-h2.yml`, `application-docker.yml`
- Docs: `docs/architecture.md`, `README.md`

**Responsibilities**

- Import the baseline as the first commit on `main`, then create the shared GitHub repository.
- Keep the backend build and shared settings working. Review any PR that changes them.
- Explain the proxy design in the presentation: request flow, fallback, streaming.

**Week 1 work**

1. Run the backend with the H2 profile and send real requests through it with a real provider key: OpenAI format (normal and streaming) and Anthropic format.
2. Turn off the first provider and check that the request falls back to the second one.
3. Update `docs/architecture.md`: the request flow with the class names (`ProxyController` → `ProxyService` → `ProviderRouter` → `UpstreamClient` → `SseProcessor`), and the settings in `application.yml`.
4. Fix any problem found and commit it.

**Depends on:** Member 2's `KeyInfo`, `LimitChecker` and `UsageRecorder` (the proxy calls them). Agree with Member 2 before changing their signatures.

**Acceptance criteria**

- [ ] `cd backend && ./mvnw package` succeeds on a clean clone.
- [ ] Normal and streaming requests in both formats work through the gateway and appear in the request log.
- [ ] Fallback to a second provider works.
- [ ] `docs/architecture.md` describes the request flow and settings.
- [ ] PR merged after review by at least one other member.

## Member 2: Xiaohan Wang, keys, auth and accounting

**Files**

- `backend/src/main/java/com/tokenfest/gateway/keys/`
- `backend/src/main/java/com/tokenfest/gateway/auth/`
- `backend/src/main/java/com/tokenfest/gateway/limits/`
- `backend/src/main/java/com/tokenfest/gateway/usage/`
- `backend/src/main/java/com/tokenfest/gateway/alerts/`
- `backend/src/main/java/com/tokenfest/gateway/stats/`
- `backend/src/main/java/com/tokenfest/gateway/export/`
- `backend/src/main/java/com/tokenfest/gateway/settings/`
- `backend/src/main/java/com/tokenfest/gateway/user/`
- `backend/src/main/java/com/tokenfest/gateway/maintenance/`
- `backend/src/main/java/com/tokenfest/gateway/domain/`
- `backend/src/main/java/com/tokenfest/gateway/repository/`
- `backend/src/main/resources/db/migration/`
- Docs: `docs/api.md`, `docs/database.md`

**Responsibilities**

- Own the admin and member REST API and the database schema.
- Explain in the presentation how keys, budgets, rate limits, cost and alerts work.

**Week 1 work**

1. Read the module and `docs/api.md` and `docs/database.md`. Note anything unclear or wrong.
2. With the backend running, go through each flow with `curl`: admin login; create, update, disable, regenerate and delete a key; key login; a key with a small monthly budget (check the 429 once it is used up); a key with a rate limit; alert settings and a webhook alert; stats; CSV export; the member API.
3. Add a **"Walkthrough with curl"** section to `docs/api.md` with the commands you used.
4. Fix any problem found, or open a GitHub issue for it if it belongs to another module.

**Depends on:** Member 1's proxy (it produces the usage records); Member 3 uses this API from the dashboard. Tell Member 3 before changing any response field.

**Acceptance criteria**

- [ ] Every admin and user endpoint in `docs/api.md` was called at least once and works as described.
- [ ] Budget 429 and rate-limit 429 checked by hand.
- [ ] `docs/api.md` has the curl walkthrough.
- [ ] Any schema change is a new migration file (`V2__...sql`); `V1__init.sql` is unchanged.
- [ ] PR merged after review by at least one other member.

## Member 3: Mingzhu Wan, dashboard and deployment

**Files**

- `frontend/` (all of it: `src/`, `package.json`, `vite.config.ts`, `nginx.conf`, `Dockerfile`)
- `docker-compose.yml`, `backend/Dockerfile`, `backend/.dockerignore`, `.env.example`
- Docs: `docs/user-guide.md`, `docs/deployment.md`, `docs/wireframes.md`

**Responsibilities**

- Own the dashboard and the Docker deployment.
- Do the live demo in the presentation.

**Week 1 work**

1. Run the full stack with `docker compose up -d --build` (see README) and log in as admin.
2. Go through every page: Overview, Requests, Providers, Keys, Settings, and the member dashboard (log in with a member key). Compare with `docs/wireframes.md`.
3. Fix the lint warning at `frontend/src/components/ui.tsx:75` (a ref is read during render). `npm run lint` should then show no warnings.
4. Update `docs/user-guide.md` with screenshots of the main pages, and check that `docs/deployment.md` matches what you did.
5. Fix any UI problem found.

**Depends on:** Member 2's API (`frontend/src/api.ts`, `frontend/src/types.ts` must match `docs/api.md`); Member 1's backend build for the Docker image.

**Acceptance criteria**

- [ ] `docker compose up -d --build` starts all three services on a clean clone, and the dashboard opens on http://localhost:8080.
- [ ] Every page loads and shows the data of a test request.
- [ ] `cd frontend && npm run build && npm run lint` pass with no warnings.
- [ ] User guide has up-to-date screenshots.
- [ ] PR merged after review by at least one other member.

---

## Avoiding conflicts

- The three modules don't share source files. Shared files have one owner: `backend/pom.xml` and `application*.yml` (Member 1), `db/migration/` (Member 2), `frontend/package.json` and `docker-compose.yml` (Member 3).
- `README.md` is Member 1's. Others suggest changes in their PR description.
- Merge order for Week 1: Member 1 → Member 2 → Member 3. Before opening a PR, merge the latest `main` into your branch (see `GIT_WORKFLOW.md`).
