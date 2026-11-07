# API Design

Base URL: `http://localhost:8080` (with Docker Compose, nginx serves everything on this port). All bodies are JSON.

## 1. Proxy API (for apps and AI tools)

Authenticate with a TokenFest key, either `Authorization: Bearer tf-...` or `x-api-key: tf-...`.

| Method | Path | Format | Notes |
|---|---|---|---|
| POST | `/v1/chat/completions` | OpenAI | `"stream": true` returns Server-Sent Events |
| GET | `/v1/models` | OpenAI | Models the key can use |
| POST | `/v1/messages` | Anthropic | `"stream": true` returns Server-Sent Events |

Client setup:

- OpenAI SDK: `base_url = "http://localhost:8080/v1"`, `api_key = "tf-..."`
- Anthropic SDK / Claude Code: `ANTHROPIC_BASE_URL=http://localhost:8080`, `ANTHROPIC_API_KEY=tf-...`

How a request is handled:

1. Check the key (401 if missing, unknown, or disabled).
2. Check the rate limit and the monthly budget (429).
3. Find enabled providers of the same format that serve the model and that the key may use, ordered by priority (404 if none).
4. Forward to the first provider. On 429, 5xx, timeout, or connection error, try the next one. Other 4xx errors are returned to the client as they are.
5. Record tokens, cost, latency, and the request in `request_logs` and `usage_daily` (in the background, so the response is never delayed).

Streaming: the response is passed to the client one SSE event at a time. For OpenAI streams the gateway adds `stream_options.include_usage` so it can count tokens; if the client didn't ask for usage, that extra chunk is removed before it reaches the client. Fallback only happens before the first byte is sent.

Cost: `tokens × price per 1M`. Anthropic cache writes cost 1.25× and cache reads 0.1× the input price.

Errors created by the gateway use the format of the endpoint:

```json
// OpenAI endpoints
{"error": {"message": "Monthly budget exceeded", "type": "tokenfest_error", "code": "budget_exceeded"}}
// Anthropic endpoint
{"type": "error", "error": {"type": "rate_limit_error", "message": "Monthly budget exceeded"}}
```

| Status | Code | When |
|---|---|---|
| 401 | `invalid_api_key` | Key missing, unknown, or disabled |
| 429 | `rate_limit_exceeded` | Over requests per minute (`Retry-After` header is set) |
| 429 | `budget_exceeded` | Monthly budget used up |
| 404 | `model_not_found` | No provider serves the model for this key |
| 502 | `upstream_error` | All providers failed |

## 2. Auth API

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/login` | `{"username", "password"}` | `{"token", "role": "ADMIN", "name"}` |
| POST | `/api/auth/key-login` | `{"apiKey"}` | `{"token", "role": "USER", "name"}` |

The dashboard sends the JWT as `Authorization: Bearer <token>` on every `/api/admin/*` and `/api/user/*` call. Tokens expire after 12 hours. Both login endpoints allow 10 attempts per minute per client address (then 429).

## 3. Admin API (role ADMIN)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/admin/providers` | API keys are never returned, only `hasApiKey` |
| POST | `/api/admin/providers` | `{name, type, baseUrl, apiKey, priority, enabled, models: [{modelName, inputPrice, outputPrice}]}` |
| PUT | `/api/admin/providers/{id}` | Same body; leave `apiKey` empty to keep the current one |
| DELETE | `/api/admin/providers/{id}` | |
| GET | `/api/admin/keys` | Includes this month's spending |
| POST | `/api/admin/keys` | `{name, monthlyBudget, rateLimitPerMinute, allowedProviderIds}`; the response contains the full key **once** |
| PUT | `/api/admin/keys/{id}` | Same body plus `enabled` |
| DELETE | `/api/admin/keys/{id}` | Also deletes the key's history |
| GET | `/api/admin/stats/summary?from&to` or `?days` | Total cost, requests, tokens, error count |
| GET | `/api/admin/stats/daily?from&to&groupBy=key\|model\|provider` | One row per day and group |
| GET | `/api/admin/requests?keyId&providerId&model&status&from&to&page&size` | Paged, newest first |
| GET | `/api/admin/requests/{id}` | Includes request and response bodies |
| GET | `/api/admin/settings` | Alert thresholds, webhook URL, emails |
| PUT | `/api/admin/settings` | |
| POST | `/api/admin/settings/test-alert` | Sends a test message |
| GET | `/api/admin/alerts` | Recent alert events |
| GET | `/api/admin/export?month=2026-10` | CSV download |
| PUT | `/api/admin/account/password` | `{currentPassword, newPassword}` (min 8 characters) |

Dates are `yyyy-MM-dd`; `from` and `to` are inclusive. Instead of `from`/`to` you can pass `days=N` (the last N days ending today). Days are counted in the server's time zone (`TOKENFEST_TIME_ZONE`, default UTC).

## 4. User API (role USER, own data only)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/user/me` | Name, key prefix, budget, spent this month, rate limit |
| GET | `/api/user/stats/summary?days` | Own totals |
| GET | `/api/user/stats/daily?days` | Own daily usage by model |
| GET | `/api/user/requests?page&size` | Own request history |
| GET | `/api/user/requests/{id}` | |
| GET | `/api/user/export?month=2026-10` | Own CSV |

## 5. Other

| Method | Path | Notes |
|---|---|---|
| GET | `/api/info` | Name and version, no auth |
| GET | `/actuator/health` | Health check, no auth |
