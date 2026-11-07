# Deployment

## Docker Compose

```bash
git clone <repo> && cd TokenFest
cp .env.example .env
```

Edit `.env`:

| Variable | Required | Meaning |
|---|---|---|
| `TOKENFEST_ENCRYPTION_KEY` | yes | `openssl rand -base64 32`. Encrypts provider keys and signs logins. **Back it up**: if it changes, provider keys must be entered again and everyone must log in again |
| `TOKENFEST_ADMIN_PASSWORD` | recommended | Password of the first admin `admin` (otherwise printed in the log) |
| `POSTGRES_PASSWORD` | recommended | Database password (default `tokenfest`) |
| `TOKENFEST_PORT` | no | Published port (default 8080) |
| `TOKENFEST_TIME_ZONE` | no | Zone for budget months and charts, e.g. `America/New_York` (default UTC) |
| `TOKENFEST_LOG_RETENTION_DAYS` | no | Days to keep request logs with bodies (default 90, `0` = forever). Daily totals are always kept |
| `SPRING_MAIL_*`, `TOKENFEST_ALERT_FROM` | no | SMTP for email alerts, see below |

Start:

```bash
docker compose up -d --build
docker compose logs -f backend     # wait for "Started GatewayApplication"
```

What runs:

```
browser / SDKs ──► frontend (nginx, port 8080)
                     ├─ /           dashboard (static files)
                     ├─ /api/       ─► backend:8080
                     └─ /v1/        ─► backend:8080 (streaming, no buffering)
                   backend (Spring Boot) ──► postgres (internal; bound to 127.0.0.1:5432)
```

Only nginx is published. The backend trusts the client address that nginx sets (`X-Forwarded-For`), which the login throttle uses.

## HTTPS

Keys travel in headers, so use HTTPS outside a trusted network. Put a TLS proxy in front of port 8080, for example Caddy:

```
gateway.example.com {
    reverse_proxy localhost:8080 {
        flush_interval -1
    }
}
```

`flush_interval -1` keeps streaming responses live.

## Email alerts

Add to `.env` (example for Gmail with an app password):

```
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=you@gmail.com
SPRING_MAIL_PASSWORD=your-app-password
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
TOKENFEST_ALERT_FROM=you@gmail.com
```

Then `docker compose up -d` and use **Settings → Send test alert**.

## Backups

```bash
docker compose exec postgres pg_dump -U tokenfest tokenfest > tokenfest-$(date +%F).sql
# restore into an empty database:
docker compose exec -T postgres psql -U tokenfest tokenfest < tokenfest-2026-10-07.sql
```

Keep the `.env` file (encryption key) with the backup.

## Upgrading

```bash
git pull
docker compose up -d --build
```

Database changes are applied automatically by Flyway on startup.

## Without Docker

Build the jar (`cd backend && ./mvnw package -DskipTests`) and run it with Java 21+:

```bash
TOKENFEST_ENCRYPTION_KEY=... DB_URL=jdbc:postgresql://localhost:5432/tokenfest DB_USERNAME=... DB_PASSWORD=... \
  java -jar backend/target/gateway-0.0.1-SNAPSHOT.jar
```

Serve `frontend/dist` (from `npm run build`) with any web server that forwards `/api` and `/v1` to the backend (see `frontend/nginx.conf`).
