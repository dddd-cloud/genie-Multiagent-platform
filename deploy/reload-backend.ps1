# Compile changed Java/resources in the running backend and let Spring DevTools
# restart the application context. Does not recreate the container.
$ErrorActionPreference = "Stop"
$container = "joyagent-mvp-genie-backend-1"

docker exec $container bash -lc "/workspace/genie-backend/scripts/fast-compile.sh"
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}
Write-Host "Compiled. DevTools should reload the app in a few seconds (watch logs for 'Restarting')."
