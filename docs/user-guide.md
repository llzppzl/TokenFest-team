# User Guide

## For admins

### 1. Log in

Open the dashboard (for example http://localhost:8080) and log in as `admin` with the password from `TOKENFEST_ADMIN_PASSWORD`. If none was set, the password was printed once in the backend log (`docker compose logs backend | grep "Created admin"`). Change it in **Settings → Change password**.

### 2. Add providers

**Providers → Add provider**

| Field | Example |
|---|---|
| API format | *OpenAI-compatible* for OpenAI, DeepSeek, OpenRouter, local servers; *Anthropic* for Claude |
| Base URL | `https://api.openai.com/v1`, `https://api.deepseek.com/v1` (include `/v1`); `https://api.anthropic.com` (without `/v1`) |
| API key | The provider's key. It is encrypted and never shown again; leave empty when editing to keep it |
| Priority | Lower is tried first. Give a backup provider a higher number |
| Models | Exact model names (e.g. `gpt-4o`, `claude-sonnet-4-5`) with prices in USD per 1M input/output tokens from the provider's pricing page |

**Fallback:** if two providers list the same model, requests go to the lower priority first and move to the next one on 429, 5xx, timeouts or connection errors.

### 3. Create keys

**Keys → New key**: one key per person (or per app). Set a monthly budget and a rate limit if you want, and optionally limit the key to some providers. The full key (`tf-...`) is shown **once**; copy it and send it to the person. Disable a key to block it but keep its history; deleting also deletes its history.

### 4. Alerts

**Settings → Budget alerts**: thresholds (default 50, 80, 100), a webhook URL (a Slack incoming webhook works) and/or email addresses (needs SMTP, see [deployment](deployment.md)). Use **Send test alert** to check. Each key gets one alert per threshold per month; history is shown below.

### 5. Watch spending

- **Overview**: cost, requests, tokens and errors; cost per day by key, model or provider; budget bars; **Export CSV** for a month.
- **Requests**: every request with filters; click one to see the attempts, error, and the request and response bodies.

## For team members

### Connect your tools

Use the `tf-...` key from your admin instead of the provider's key, and change the base URL:

| Tool | Setting |
|---|---|
| OpenAI Python/JS SDK | `base_url="http://<gateway>:8080/v1"`, `api_key="tf-..."` |
| Anthropic SDK | `base_url="http://<gateway>:8080"`, `api_key="tf-..."` |
| Claude Code | `export ANTHROPIC_BASE_URL=http://<gateway>:8080` and `export ANTHROPIC_API_KEY=tf-...` |
| curl | `curl http://<gateway>:8080/v1/chat/completions -H "Authorization: Bearer tf-..." -H "Content-Type: application/json" -d '{"model":"gpt-4o","messages":[{"role":"user","content":"Hi"}]}'` |

Use model names your admin added (`GET /v1/models` lists them).

### See your usage

Open the dashboard, choose **Team member**, and paste your key. You see this month's spending against your budget, cost per day by model, your requests, and a CSV export. You can't see other people's data.

## Errors

| Status | Code | Meaning | What to do |
|---|---|---|---|
| 401 | `invalid_api_key` | Key missing, wrong or disabled | Check the key; ask your admin |
| 404 | `model_not_found` | No provider serves this model for your key | Check the model name (`/v1/models`) |
| 429 | `rate_limit_exceeded` | Too many requests this minute | Wait `Retry-After` seconds |
| 429 | `budget_exceeded` | Monthly budget used up | Wait for next month or ask your admin to raise it |
| 502 | `upstream_error` | No provider could be reached | Try again; admin checks Providers and the request log |
| other 4xx/5xx | (provider's own error) | Returned as the provider sent it | Read the message |
