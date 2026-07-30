# Local MVP environment

The tracked Compose files contain no passwords, tokens, or model API keys.
Each developer keeps those values in a private root `.env` file.

## 1. Create the local environment file

PowerShell:

```powershell
Copy-Item .env.example .env
```

Bash:

```bash
cp .env.example .env
```

Fill the empty required values in `.env`:

- `GENIE_DB_PASSWORD`
- `GENIE_BOOTSTRAP_ADMIN_PASSWORD`
- `GENIE_INTERNAL_AGENT_TOKEN`

The two `MVP_ACCEPTANCE_*` values are local acceptance credentials only and
must never be reused in production.

## 2. Start with Fake Agent

Fake Agent requires no model API key and is the recommended first startup:

```powershell
docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml config --quiet

docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml up -d
```

## 3. Start with a real OpenAI-compatible model

Set these values in the private `.env`:

```dotenv
DEFAULT_MODEL=<model-name>
OPENAI_BASE_URL=<openai-compatible-base-url>
OPENAI_API_KEY=<private-api-key>
```

Then start with the additional real-model override:

```powershell
docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml `
  -f deploy/docker-compose.real-model.override.yml config --quiet

docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml `
  -f deploy/docker-compose.real-model.override.yml up -d
```

Open <http://localhost:3000> after the UI production build completes.

## Useful commands

```powershell
docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml ps

docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml logs -f

docker compose --env-file .env `
  -f deploy/docker-compose.mvp.yml `
  -f deploy/docker-compose.local.override.yml stop
```

`stop` preserves the MySQL volume. Bootstrap administrator credentials are
applied when the database is initialized; changing `.env` later does not
automatically change an existing administrator password.
