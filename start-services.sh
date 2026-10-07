#!/bin/bash

# LEAPvengers Docker Services Management Script
# Usage: 
#   ./start-services.sh         # Start services
#   ./start-services.sh stop    # Stop services
#   ./start-services.sh restart # Restart services
#   ./start-services.sh logs    # View logs

set -e

ACTION="${1:-start}"

# Colors
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

function write_success {
    echo -e "${GREEN}✓ $1${NC}"
}

function write_info {
    echo -e "${CYAN}ℹ $1${NC}"
}

function write_warning {
    echo -e "${YELLOW}⚠ $1${NC}"
}

function write_error {
    echo -e "${RED}✗ $1${NC}"
}

function check_docker {
    write_info "Checking Docker installation..."
    if ! command -v docker &> /dev/null; then
        write_error "Docker is not installed or not in PATH"
        write_error "Please install Docker from https://www.docker.com/products/docker-desktop"
        exit 1
    fi
    write_success "Docker found: $(docker --version)"
}

function check_docker_compose {
    write_info "Checking Docker Compose installation..."
    if ! docker compose version &> /dev/null; then
        write_error "Docker Compose is not available"
        write_error "Please ensure Docker Compose is installed (included with Docker Desktop)"
        exit 1
    fi
    write_success "Docker Compose found"
}

function start_services {
    echo ""
    write_info "================================"
    write_info "Starting LEAPvengers Services"
    write_info "================================"
    echo ""
    
    check_docker
    check_docker_compose
    
    echo ""
    write_info "Starting containers from docker-compose.yml..."
    docker compose up -d
    
    write_success "Containers started"
    echo ""
    write_info "Services are starting up. Waiting for health checks..."
    
    # Wait for PostgreSQL
    write_info "Waiting for PostgreSQL (leapdb)..."
    timeout=0
    while [ $timeout -lt 30 ]; do
        if docker exec leapdb pg_isready -U postgres > /dev/null 2>&1; then
            write_success "PostgreSQL is healthy"
            break
        fi
        sleep 1
        ((timeout++))
    done
    
    # Wait for SonarQube
    write_info "Waiting for SonarQube (leapsonar)..."
    timeout=0
    while [ $timeout -lt 60 ]; do
        if curl -s http://localhost:9000/api/system/health | grep -q "UP" 2>/dev/null; then
            write_success "SonarQube is ready"
            break
        fi
        sleep 2
        ((timeout+=2))
    done
    
    echo ""
    write_success "================================"
    write_success "All services are ready!"
    write_success "================================"
    echo ""
    write_info "Service endpoints:"
    write_info "  PostgreSQL:  localhost:5432 (user: postgres)"
    write_info "  SonarQube:   http://localhost:9000 (user: admin, password: admin)"
    echo ""
    write_info "Useful commands:"
    write_info "  docker compose logs -f          # View all logs (follow)"
    write_info "  docker compose logs -f leapdb   # View PostgreSQL logs"
    write_info "  docker compose logs -f leapsonar # View SonarQube logs"
    write_info "  docker compose ps               # Show container status"
    write_info "  docker compose stop             # Stop services"
    write_info "  docker compose down             # Stop & remove containers"
}

function stop_services {
    echo ""
    write_info "Stopping LEAPvengers Services..."
    docker compose stop
    write_success "Services stopped"
}

function restart_services {
    echo ""
    write_info "Restarting LEAPvengers Services..."
    docker compose restart
    write_success "Services restarted"
}

function show_logs {
    echo ""
    write_info "Following container logs (Ctrl+C to exit)..."
    echo ""
    docker compose logs -f
}

# Main
case $ACTION in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        restart_services
        ;;
    logs)
        show_logs
        ;;
    *)
        write_warning "Unknown action: $ACTION"
        echo ""
        write_info "Usage:"
        write_info "  ./start-services.sh         # Start services"
        write_info "  ./start-services.sh stop    # Stop services"
        write_info "  ./start-services.sh restart # Restart services"
        write_info "  ./start-services.sh logs    # View logs"
        exit 1
        ;;
esac
