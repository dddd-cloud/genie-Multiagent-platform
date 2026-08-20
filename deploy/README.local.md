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

Optional Phase2 variables (safe to leave empty/false for Stage 1 MVP).
Compose keeps `:-` defaults so non-Phase2 startups are not blocked; Phase2
backend beans fail-fast when MCP crypto/token are missing at runtime.

**Stage 2 real Phase2 gate** requires these set before `pnpm build` / stack up:

- `VITE_PHASE2_ENABLED=true` — UI Phase2 feature flag (baked in at `pnpm build`)
- `GENIE_MCP_CREDENTIAL_KEY` — 32-byte key, base64-encoded (MCP credential encryption)
- `GENIE_INTERNAL_MCP_TOKEN` — internal token for MCP-related service calls

Then run real E2E with `PHASE2_REAL_E2E_READY=1` (see `scripts/acceptance/phase2/d/real_e2e.sh`).

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

## Faster day-to-day reload

Do **not** `--force-recreate` the backend for ordinary Java edits. Recreating
the container is what made compile+start feel like several minutes: javac was
writing hundreds of class files through the Windows bind mount, then Spring
Boot still needed ~50s.

After the local override is applied once (`up -d --no-deps genie-backend`):

- **Java change (fast):** `.\deploy\reload-backend.ps1`  
  Compiles only the changed files (javac + Lombok) and Spring DevTools reloads
  the app in a few seconds. Do not recreate the container.
- **Java change (cold):** `docker restart joyagent-mvp-genie-backend-1`  
  Use this if DevTools reload fails, or after a large refactor. Compile is
  skipped when sources are unchanged.
- **Frontend change:** `docker exec joyagent-mvp-ui-1 bash -lc "cd /workspace/ui && pnpm build"`  
  Then Ctrl+F5. Do not restart the backend.
- **pom.xml / new Maven dependency:** set `GENIE_BACKEND_REPACKAGE=1` and
  recreate `genie-backend` once (full `mvn package`).

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
