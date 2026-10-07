# LEAPvengers Docker Services Management Script
# Usage: 
#   .\start-services.ps1         # Start services
#   .\start-services.ps1 stop    # Stop services
#   .\start-services.ps1 restart # Restart services
#   .\start-services.ps1 logs    # View logs

param(
    [string]$action = "start"
)

$ErrorActionPreference = "Stop"

function Write-Success {
    param([string]$message)
    Write-Host $message -ForegroundColor Green
}

function Write-Info {
    param([string]$message)
    Write-Host $message -ForegroundColor Cyan
}

function Write-Warning-Custom {
    param([string]$message)
    Write-Host $message -ForegroundColor Yellow
}

function Write-Error-Custom {
    param([string]$message)
    Write-Host $message -ForegroundColor Red
}

function Check-Docker {
    Write-Info "Checking Docker installation..."
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        Write-Error-Custom "ERROR - Docker is not installed or not in PATH"
        Write-Error-Custom "Please install Docker Desktop from https://www.docker.com/products/docker-desktop"
        exit 1
    }
    Write-Success "OK - Docker found: $(docker --version)"
}

function Check-DockerCompose {
    Write-Info "Checking Docker Compose installation..."
    $compose = docker compose version 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error-Custom "ERROR - Docker Compose is not available"
        Write-Error-Custom "Please ensure Docker Compose is installed (included with Docker Desktop)"
        exit 1
    }
    Write-Success "OK - Docker Compose found"
}

function Start-Services {
    Write-Info ""
    Write-Info "================================"
    Write-Info "Starting LEAPvengers Services"
    Write-Info "================================"
    Write-Info ""
    
    Check-Docker
    Check-DockerCompose
    
    Write-Info ""
    Write-Info "Starting containers from docker-compose.yml..."
    docker compose up -d
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "OK - Containers started successfully"
        Write-Info ""
        Write-Info "Services are starting up. Waiting for health checks..."
        
        # Wait for PostgreSQL
        Write-Info "Waiting for PostgreSQL (leapdb)..."
        $timeout = 0
        while ($timeout -lt 30) {
            $health = docker compose ps leapdb --format "table {{.Status}}" 2>&1
            if ($health -match "healthy") {
                Write-Success "✓ PostgreSQL is healthy"
                break
            }
            Start-Sleep -Seconds 1
            $timeout++
        }
        
        # Wait for SonarQube
        Write-Info "Waiting for SonarQube (leapsonar)..."
        $timeout = 0
        while ($timeout -lt 60) {
            try {
                $response = Invoke-WebRequest -Uri "http://localhost:9000/api/system/health" -UseBasicParsing -ErrorAction SilentlyContinue
                if ($response.StatusCode -eq 200) {
                    Write-Success "✓ SonarQube is ready"
                    break
                }
            } catch {
                # Not ready yet
            }
            Start-Sleep -Seconds 2
            $timeout += 2
        }
        
        Write-Info ""
        Write-Success "================================"
        Write-Success "All services are ready!"
        Write-Success "================================"
        Write-Info ""
        Write-Info "Service endpoints:"
        Write-Info "  PostgreSQL:  localhost:5432 (user: postgres)"
        Write-Info "  SonarQube:   http://localhost:9000 (user: admin, password: admin)"
        Write-Info ""
        Write-Info "Useful commands:"
        Write-Info "  docker compose logs -f          # View all logs (follow)"
        Write-Info "  docker compose logs -f leapdb   # View PostgreSQL logs"
        Write-Info "  docker compose logs -f leapsonar # View SonarQube logs"
        Write-Info "  docker compose ps               # Show container status"
        Write-Info "  docker compose stop             # Stop services"
        Write-Info "  docker compose down             # Stop & remove containers"
    } else {
        Write-Error-Custom "ERROR - Failed to start containers"
        exit 1
    }
}

function Stop-Services {
    Write-Info ""
    Write-Info "Stopping LEAPvengers Services..."
    docker compose stop
    if ($LASTEXITCODE -eq 0) {
        Write-Success "OK - Services stopped"
    } else {
        Write-Error-Custom "ERROR - Failed to stop services"
        exit 1
    }
}

function Restart-Services {
    Write-Info ""
    Write-Info "Restarting LEAPvengers Services..."
    docker compose restart
    if ($LASTEXITCODE -eq 0) {
        Write-Success "OK - Services restarted"
    } else {
        Write-Error-Custom "ERROR - Failed to restart services"
        exit 1
    }
}

function Show-Logs {
    Write-Info ""
    Write-Info "Following container logs (Ctrl+C to exit)..."
    Write-Info ""
    docker compose logs -f
}

# Main
switch ($action.ToLower()) {
    "start" { Start-Services }
    "stop" { Stop-Services }
    "restart" { Restart-Services }
    "logs" { Show-Logs }
    default {
        Write-Warning-Custom "Unknown action: $action"
        Write-Info ""
        Write-Info "Usage:"
        Write-Info "  .\start-services.ps1         # Start services"
        Write-Info "  .\start-services.ps1 stop    # Stop services"
        Write-Info "  .\start-services.ps1 restart # Restart services"
        Write-Info "  .\start-services.ps1 logs    # View logs"
        exit 1
    }
}
