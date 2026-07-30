param(
    [string]$JavaHome,
    [string]$MavenCmd
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
try {
    chcp 65001 > $null
} catch {
    # Ignore hosts that do not expose chcp.
}

function Write-Stage {
    param([string]$Message)
    Write-Host ""
    Write-Host $Message
}

function Resolve-JavaHome {
    param([string]$Candidate)
    $candidates = @()
    if ($Candidate) { $candidates += $Candidate }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += "E:\dev-tools\jdk-17"

    foreach ($item in $candidates) {
        if ($item -and (Test-Path (Join-Path $item "bin\java.exe"))) {
            return (Resolve-Path $item).Path
        }
    }
    Write-Error "Java 17 was not found. Pass -JavaHome, set JAVA_HOME, or install portable JDK at E:\dev-tools\jdk-17."
    exit 11
}

function Resolve-MavenCmd {
    param([string]$Candidate)
    if ($Candidate -and (Test-Path $Candidate)) {
        return (Resolve-Path $Candidate).Path
    }

    $pathMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($pathMaven) {
        return $pathMaven.Source
    }

    $fallback = "E:\dev-tools\apache-maven-3.9.16\bin\mvn.cmd"
    if (Test-Path $fallback) {
        return $fallback
    }

    Write-Error "Maven was not found. Pass -MavenCmd, add mvn.cmd to PATH, or install portable Maven at E:\dev-tools\apache-maven-3.9.16."
    exit 12
}

$repoRoot = (Get-Location).Path
if (-not (Test-Path (Join-Path $repoRoot "genie-backend\pom.xml"))) {
    Write-Error "Please run this script from the repository root."
    exit 10
}

$resolvedJavaHome = Resolve-JavaHome -Candidate $JavaHome
$resolvedMaven = Resolve-MavenCmd -Candidate $MavenCmd
$env:JAVA_HOME = $resolvedJavaHome
$env:PATH = "$resolvedJavaHome\bin;$env:PATH"

Write-Stage "[0/4] Check Docker, Java, and Maven"
try {
    docker version
} catch {
    Write-Error "Docker is not available. Please start Docker Desktop and retry."
    exit 20
}
if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker is not running or cannot be reached. Please start Docker Desktop and retry."
    exit 20
}

$javaExe = Join-Path $resolvedJavaHome "bin\java.exe"
cmd /c "`"$javaExe`" -version 2>&1"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Java version check failed."
    exit 21
}

& $resolvedMaven -version
if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven version check failed."
    exit 22
}

Write-Stage "[1/4] Start real MySQL test environment"
Write-Host "Testcontainers will detect Docker Desktop and start mysql:8.0.36."

Write-Stage "[2/4] Conversation CRUD demo"
Write-Host "The demo test creates conversations, lists with pagination, queries detail, and renames a conversation."

Write-Stage "[3/4] Tenant/owner data isolation demo"
Write-Host "The demo test switches FakeCurrentUserProvider in test scope and verifies RESOURCE_NOT_FOUND for other users."

Write-Stage "[4/4] Soft delete demo"
Write-Host "The demo test deletes a conversation, verifies business invisibility, and reads deleted_at from MySQL."

$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
Push-Location (Join-Path $repoRoot "genie-backend")
try {
    & $resolvedMaven `
        "-Dtest=ConversationMysqlDemoTest" `
        "-Dsurefire.useFile=false" `
        "-DtrimStackTrace=false" `
        test
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
    $stopwatch.Stop()
}

if ($exitCode -ne 0) {
    Write-Error "MVP-B conversation MySQL demo failed. Maven exit code: $exitCode"
    exit $exitCode
}

Write-Host ""
Write-Host "Total demo time: $([math]::Round($stopwatch.Elapsed.TotalSeconds, 1)) seconds"
Write-Host "========================================"
Write-Host "MVP-B Conversation MySQL Demo: PASS"
Write-Host "========================================"