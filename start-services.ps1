# LEAPvengers Docker Services Management Script
# Usage:
#   .\start-services.ps1
#   .\start-services.ps1 stop
#   .\start-services.ps1 restart
#   .\start-services.ps1 logs

$ErrorActionPreference = "Stop"

if ($args.Count -gt 0) {
    $action = [string]$args[0]
} else {
    $action = "start"
}

function Write-Success {
    param([string]$message)
    Write-Host $message -ForegroundColor Green
}

function Write-Info {
    param([string]$message)
    Write-Host $message -ForegroundColor Cyan
}

function Write-WarningCustom {
    param([string]$message)
    Write-Host $message -ForegroundColor Yellow
}

function Write-ErrorCustom {
    param([string]$message)
    Write-Host $message -ForegroundColor Red
}

function Assert-DockerInstalled {
    Write-Info "Checking Docker installation..."
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        Write-ErrorCustom "ERROR - Docker is not installed or not in PATH"
        Write-ErrorCustom "Please install Docker Desktop from https://www.docker.com/products/docker-desktop"
        exit 1
    }

    Write-Success "OK - Docker found: $(docker --version)"
}

function Assert-DockerComposeInstalled {
    Write-Info "Checking Docker Compose installation..."
    docker compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorCustom "ERROR - Docker Compose is not available"
        Write-ErrorCustom "Please ensure Docker Compose is installed (included with Docker Desktop)"
        exit 1
    }

    Write-Success "OK - Docker Compose found"
}

function Wait-ForPostgres {
    Write-Info "Waiting for PostgreSQL (leapdb)..."
    $timeout = 0
    while ($timeout -lt 30) {
        $health = docker compose ps leapdb --format "table {{.Status}}" 2>&1
        if ($health -match "healthy") {
            Write-Success "OK - PostgreSQL is healthy"
            return
        }

        Start-Sleep -Seconds 1
        $timeout++
    }

    Write-WarningCustom "WARN - PostgreSQL did not report healthy status within 30 seconds"
}

function Wait-ForApp {
    Write-Info "Waiting for the app (the first start also builds the images and loads a year of prices)..."
    $maxAttempts = 150
    $attempt = 0

    while ($attempt -lt $maxAttempts) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/api/health" -UseBasicParsing -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Success "OK - The app is ready"
                return
            }
        }
        catch {
            # Not ready yet
        }

        $attempt++
        Start-Sleep -Seconds 2
    }

    Write-WarningCustom "WARN - The app was not ready within 5 minutes; check: docker compose logs db-init backend"
}

function Start-Services {
    Write-Info ""
    Write-Info "================================"
    Write-Info "Starting LEAPvengers Services"
    Write-Info "================================"
    Write-Info ""

    Assert-DockerInstalled
    Assert-DockerComposeInstalled

    Write-Info ""
    Write-Info "Starting containers from docker-compose.yml..."
    docker compose up --build -d
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorCustom "ERROR - Failed to start containers"
        exit 1
    }

    Write-Success "OK - Containers started successfully"
    Write-Info ""
    Write-Info "Services are starting up. Waiting for health checks..."

    Wait-ForPostgres
    Wait-ForApp

    Write-Info ""
    Write-Success "================================"
    Write-Success "All services are ready!"
    Write-Success "================================"
    Write-Info ""
    Write-Info "Service endpoints:"
    Write-Info "  App:        http://localhost:8080 (sign in as joanna, david or priya; password Test123)"
    Write-Info "  API:        http://localhost:8081/api"
    Write-Info "  PostgreSQL: localhost:5432 (user: postgres)"
    Write-Info ""
    Write-Info "Useful commands:"
    Write-Info "  docker compose logs -f            # View all logs (follow)"
    Write-Info "  docker compose logs -f backend    # View the backend's logs"
    Write-Info "  docker compose logs -f feed       # View the live price feed"
    Write-Info "  docker compose ps                 # Show container status"
    Write-Info "  docker compose stop               # Stop services"
    Write-Info "  docker compose down               # Stop and remove containers"
}

function Stop-Services {
    Write-Info ""
    Write-Info "Stopping LEAPvengers Services..."
    docker compose stop
    if ($LASTEXITCODE -eq 0) {
        Write-Success "OK - Services stopped"
        return
    }

    Write-ErrorCustom "ERROR - Failed to stop services"
    exit 1
}

function Restart-Services {
    Write-Info ""
    Write-Info "Restarting LEAPvengers Services..."
    docker compose restart
    if ($LASTEXITCODE -eq 0) {
        Write-Success "OK - Services restarted"
        return
    }

    Write-ErrorCustom "ERROR - Failed to restart services"
    exit 1
}

function Show-Logs {
    Write-Info ""
    Write-Info "Following container logs (Ctrl+C to exit)..."
    Write-Info ""
    docker compose logs -f
}

switch ($action.ToLower()) {
    "start" { Start-Services }
    "stop" { Stop-Services }
    "restart" { Restart-Services }
    "logs" { Show-Logs }
    default {
        Write-WarningCustom "Unknown action: $action"
        Write-Info ""
        Write-Info "Usage:"
        Write-Info "  .\start-services.ps1"
        Write-Info "  .\start-services.ps1 stop"
        Write-Info "  .\start-services.ps1 restart"
        Write-Info "  .\start-services.ps1 logs"
        exit 1
    }
}
