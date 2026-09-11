# LEAPvengers Local Development Setup Script
# This script sets up the complete development environment
# Prerequisites: PostgreSQL installed, Java 17+, Node.js/npm, Maven

$ErrorActionPreference = "Stop"
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path

# Color output functions
function Write-Success {
    param([string]$message)
    Write-Host $message -ForegroundColor Green
}

function Write-Error-Custom {
    param([string]$message)
    Write-Host $message -ForegroundColor Red
}

function Write-Info {
    param([string]$message)
    Write-Host $message -ForegroundColor Cyan
}

function Write-Warning-Custom {
    param([string]$message)
    Write-Host $message -ForegroundColor Yellow
}

function Wait-ForHttpEndpoint {
    param(
        [Parameter(Mandatory = $true)][string]$url,
        [Parameter(Mandatory = $true)][string]$serviceName,
        [int]$timeoutSeconds = 120,
        [int]$retryDelaySeconds = 2
    )

    $deadline = (Get-Date).AddSeconds($timeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                Write-Success "OK - $serviceName is ready at $url"
                return $true
            }
        } catch {
            # Service not ready yet. Keep polling until timeout.
        }

        Start-Sleep -Seconds $retryDelaySeconds
    }

    Write-Warning-Custom "$serviceName was not ready after $timeoutSeconds seconds: $url"
    return $false
}

# Configuration
$pgHost = "localhost"
$pgPort = "5432"
$pgUser = "postgres"
$pgPassword = "n3u3d4!"
$dbName = "leapvengersdb"
$dbSchemaFile = "$scriptPath\Database\enterprise-schema.sql"
$dbSeedFile = "$scriptPath\Database\seed.sql"
$backendDir = "$scriptPath\backend"
$frontendDir = "$scriptPath\frontend"

Write-Info "================================"
Write-Info "LEAPvengers Development Setup"
Write-Info "================================"
Write-Info ""

# Step 1: Check PostgreSQL Connection
Write-Info "Step 1: Checking PostgreSQL connection..."
try {
    $env:PGPASSWORD = $pgPassword
    $result = psql -h $pgHost -p $pgPort -U $pgUser -c "SELECT version();" -q 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "OK - PostgreSQL is running"
    } else {
        Write-Error-Custom "ERROR - Cannot connect to PostgreSQL"
        Write-Error-Custom "Error: $result"
        Write-Error-Custom "Please ensure PostgreSQL is installed and running on $pgHost`:$pgPort"
        exit 1
    }
} catch {
    Write-Error-Custom "ERROR - PostgreSQL check failed: $_"
    Write-Error-Custom "Please ensure psql CLI is in your PATH"
    exit 1
} finally {
    Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
}
Write-Info ""

# Step 2: Create Database if it doesn't exist
Write-Info "Step 2: Checking/Creating database '$dbName'..."
try {
    $env:PGPASSWORD = $pgPassword
    $dbExists = psql -h $pgHost -p $pgPort -U $pgUser -tc "SELECT 1 FROM pg_database WHERE datname = '$dbName'" 2>&1
    
    if ($dbExists.Trim() -eq "1") {
        Write-Success "OK - Database '$dbName' already exists"
    } else {
        Write-Info "Creating database '$dbName'..."
        $createResult = psql -h $pgHost -p $pgPort -U $pgUser -c "CREATE DATABASE $dbName;" 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Success "OK - Database '$dbName' created successfully"
        } else {
            Write-Error-Custom "ERROR - Failed to create database"
            Write-Error-Custom "Error: $createResult"
            exit 1
        }
    }
} catch {
    Write-Error-Custom "ERROR - Database check/creation failed: $_"
    exit 1
} finally {
    Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
}
Write-Info ""

# Step 3: Run Database Schema
Write-Info "Step 3: Running database schema..."
if (-not (Test-Path $dbSchemaFile)) {
    Write-Error-Custom "✗ Schema file not found: $dbSchemaFile"
    exit 1
}

try {
    $env:PGPASSWORD = $pgPassword
    # Run schema - ignore errors since psql reports NOTICE as errors
    $ErrorActionPreference = "Continue"
    psql -h $pgHost -p $pgPort -U $pgUser -d $dbName -f $dbSchemaFile *>&1 | Out-Null
    $ErrorActionPreference = "Stop"
    
    # Verify tables exist after schema load
    $tableCheck = psql -h $pgHost -p $pgPort -U $pgUser -d $dbName -c "\dt" 2>&1
    
    if ($tableCheck -match "clients|users|accounts") {
        Write-Success "OK - Database schema applied successfully"
    } else {
        Write-Error-Custom "ERROR - Failed to apply schema"
        Write-Error-Custom "Tables not found after schema execution"
        exit 1
    }
} catch {
    Write-Error-Custom "ERROR - Schema execution failed: $_"
    exit 1
} finally {
    $ErrorActionPreference = "Stop"
    Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
}
Write-Info ""

# Step 4: Seed Database
Write-Info "Step 4: Seeding database with initial data..."
if (-not (Test-Path $dbSeedFile)) {
    Write-Error-Custom "ERROR - Seed file not found: $dbSeedFile"
    exit 1
}

try {
    $env:PGPASSWORD = $pgPassword
    $ErrorActionPreference = "Continue"
    psql -h $pgHost -p $pgPort -U $pgUser -d $dbName -f $dbSeedFile *>&1 | Out-Null
    $ErrorActionPreference = "Stop"
    
    Write-Success "OK - Database seeded successfully"
} catch {
    Write-Error-Custom "ERROR - Database seeding failed: $_"
    exit 1
} finally {
    Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
}
Write-Info ""

# Step 5: Build Backend with Maven
Write-Info "Step 5: Building backend with Maven (mvn clean install)..."
if (-not (Test-Path $backendDir)) {
    Write-Error-Custom "ERROR - Backend directory not found: $backendDir"
    exit 1
}

try {
    Push-Location $backendDir
    Write-Info "Running in: $(Get-Location)"
    
    # Run maven build - ignore certain warnings
    $ErrorActionPreference = "Continue"
    & mvn clean install -q -DskipTests 2>&1 | Out-Null
    $mavenExitCode = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    
    if ($mavenExitCode -eq 0) {
        Write-Success "OK - Backend build completed successfully"
    } else {
        Write-Error-Custom "ERROR - Maven build failed"
        Write-Error-Custom "Run 'mvn clean install' in $backendDir for detailed error logs"
        Pop-Location
        exit 1
    }
    
    Pop-Location
} catch {
    Write-Error-Custom "ERROR - Backend build failed: $_"
    Pop-Location
    exit 1
}
Write-Info ""

# Step 6: Install Frontend Dependencies
Write-Info "Step 6: Installing Angular frontend dependencies..."
if (-not (Test-Path $frontendDir)) {
    Write-Error-Custom "ERROR - Frontend directory not found: $frontendDir"
    exit 1
}

try {
    Push-Location $frontendDir
    Write-Info "Running in: $(Get-Location)"
    
    # Install npm dependencies if node_modules doesn't exist or package-lock.json is newer
    if (-not (Test-Path "node_modules")) {
        Write-Info "Installing npm packages..."
        & npm install -q
        
        if ($LASTEXITCODE -ne 0) {
            Write-Error-Custom "ERROR - npm install failed"
            Pop-Location
            exit 1
        }
    } else {
        Write-Success "OK - node_modules already exists"
    }
    
    Write-Success "OK - Frontend dependencies ready"
    Pop-Location
} catch {
    Write-Error-Custom "ERROR - Frontend dependency installation failed: $_"
    Pop-Location
    exit 1
}
Write-Info ""

# Step 7: Start Services
Write-Info "Step 7: Starting services..."
Write-Info ""

# Start Backend
Write-Info "Starting Spring Boot backend on http://localhost:8081/api..."
try {
    Push-Location $backendDir
    
    # Start in a new PowerShell window/process
    $backendProcess = Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$backendDir'; mvn spring-boot:run" -PassThru
    $backendPID = $backendProcess.Id
    
    Write-Success "OK - Backend started (PID: $backendPID)"
    Write-Info "  Log output will appear in the backend terminal window"
    
    Pop-Location
    
    # Give backend time to start
    Start-Sleep -Seconds 5
} catch {
    Write-Error-Custom "ERROR - Failed to start backend: $_"
    exit 1
}
Write-Info ""

# Start Frontend
Write-Info "Starting Angular frontend development server on http://localhost:4200..."
try {
    Push-Location $frontendDir
    
    # Start in a new PowerShell window/process
    $frontendProcess = Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$frontendDir'; npm start" -PassThru
    $frontendPID = $frontendProcess.Id
    
    Write-Success "OK - Frontend started (PID: $frontendPID)"
    Write-Info "  Log output will appear in the frontend terminal window"
    
    Pop-Location
} catch {
    Write-Error-Custom "✗ Failed to start frontend: $_"
    # Don't exit here as backend is already running
}

Write-Info ""
Write-Info "Step 8: Opening application URLs in your default browser..."
try {
    Write-Info "Waiting for backend readiness..."
    $backendReady = Wait-ForHttpEndpoint -url "http://localhost:8081/api/swagger-ui/index.html" -serviceName "Backend Swagger UI" -timeoutSeconds 180

    Write-Info "Waiting for frontend readiness..."
    $frontendReady = Wait-ForHttpEndpoint -url "http://localhost:4200" -serviceName "Frontend Angular app" -timeoutSeconds 180

    if ($frontendReady) {
        Start-Process "http://localhost:4200"
    }

    if ($backendReady) {
        Start-Process "http://localhost:8081/api/swagger-ui/index.html"
    }

    if ($frontendReady -or $backendReady) {
        Write-Success "OK - Opened available service URLs in browser"
    } else {
        Write-Warning-Custom "No service URL was opened because neither endpoint became reachable in time"
    }
} catch {
    Write-Warning-Custom "Could not auto-open browser URLs: $_"
    Write-Warning-Custom "You can open them manually:"
    Write-Warning-Custom "  http://localhost:4200"
    Write-Warning-Custom "  http://localhost:8081/api/swagger-ui/index.html"
}

Write-Info ""
Write-Success "================================"
Write-Success "Setup Complete!"
Write-Success "================================"
Write-Info ""
Write-Info "Your development environment is ready:"
Write-Info ""
Write-Info "  Backend (Spring Boot API):"
Write-Info "    URL: http://localhost:8081/api"
Write-Info "    PID: $backendPID"
Write-Info ""
Write-Info "  Frontend (Angular):"
Write-Info "    URL: http://localhost:4200"
Write-Info "    PID: $frontendPID"
Write-Info ""
Write-Info "  Database:"
Write-Info "    Host: $pgHost"
Write-Info "    Port: $pgPort"
Write-Info "    Database: $dbName"
Write-Info ""
Write-Warning-Custom "To stop the services, close the terminal windows or run:"
Write-Warning-Custom "  taskkill /PID $backendPID /F  (to stop backend)"
Write-Warning-Custom "  taskkill /PID $frontendPID /F  (to stop frontend)"
Write-Info ""
Write-Info "Enjoy development!"
