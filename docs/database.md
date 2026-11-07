# Database Design

PostgreSQL in production; H2 (PostgreSQL mode) for tests and for running without Docker. The schema is created by Flyway from `backend/src/main/resources/db/migration`. Never edit a migration that has been pushed; add a new `V2__...sql` file instead.

```
admin_users

providers 1───* provider_models
    *                                   
    │ api_key_providers (allowed providers, empty = all)
    *
api_keys 1───* request_logs
         1───* usage_daily
         1───* alert_events

app_settings (key/value)
```

## Tables

| Table | Purpose | Notes |
|---|---|---|
| `admin_users` | Dashboard admins | Password stored as BCrypt hash |
| `providers` | Upstream LLM accounts | `provider_type` is `OPENAI` or `ANTHROPIC`; API key encrypted with AES-GCM; lower `priority` is tried first |
| `provider_models` | Models a provider serves | Prices in USD per 1M input/output tokens |
| `api_keys` | Keys for team members | Only the SHA-256 hash is stored; `key_prefix` is shown in the dashboard; `monthly_budget` and `rate_limit_per_minute` are optional |
| `api_key_providers` | Providers a key may use | No rows means all providers |
| `request_logs` | One row per proxied request | Tokens, cost, latency, status, number of attempts, request/response bodies (truncated) |
| `usage_daily` | Totals per day, key, provider, model | Used for charts and budget checks so we don't scan `request_logs` |
| `alert_events` | Budget thresholds reached | Unique on (key, month, threshold) so an alert is never sent twice, even after a restart |
| `app_settings` | Settings changed from the dashboard | Alert thresholds, webhook URL, alert emails |

## Decisions

- **Money** is `NUMERIC`, mapped to `BigDecimal` in Java (no floating point).
- **Timestamps** are `TIMESTAMP WITH TIME ZONE` (`Instant` in Java). Days and months for budgets use the zone in `tokenfest.time-zone` (default UTC).
- **Deleting a provider** keeps its history (`provider_id` becomes NULL). **Deleting a key** deletes its history, so the dashboard suggests disabling instead.
- `request_logs` and `usage_daily` store plain ids instead of JPA relations so that writing a log never loads other entities.
