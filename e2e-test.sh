#!/bin/bash

# Portfolio E2E Test Runner
# This script sets up the complete environment and runs E2E tests
# Usage: 
#   ./e2e-test.sh           # Setup + run E2E tests (verbose + cleanup) - DEFAULT
#   ./e2e-test.sh --silent  # Setup + run E2E tests (silent + cleanup)
#   ./e2e-test.sh --setup   # Setup only (no E2E test execution)
#   ./e2e-test.sh --keep    # Setup + run E2E tests (verbose, no cleanup)

set -e  # Exit on any error

# Parse command line arguments
# DEFAULT: verbose=true, cleanup=true
VERBOSE=true
SETUP_ONLY=false
CLEANUP_AFTER=true

while [[ $# -gt 0 ]]; do
    case $1 in
        --silent)
            VERBOSE=false
            shift
            ;;
        --setup|-s)
            SETUP_ONLY=true
            CLEANUP_AFTER=false
            shift
            ;;
        --keep|-k)
            CLEANUP_AFTER=false
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --silent         Run E2E tests without verbose output (still cleanup)"
            echo "  --setup, -s      Setup environment only (no E2E tests, no cleanup)"
            echo "  --keep, -k       Keep services running after tests (no cleanup)"
            echo "  --help, -h       Show this help message"
            echo ""
            echo "Default behavior: verbose output + cleanup after tests"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Function to check if port is in use and kill process
kill_port() {
    local port=$1
    local process_name=$2
    
    if lsof -ti:$port >/dev/null 2>&1; then
        log_warning "Port $port is in use by $process_name. Killing existing process..."
        lsof -ti:$port | xargs kill -9 2>/dev/null || true
        sleep 2
    fi
}

# Function to wait for service to be ready
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=30
    local attempt=1
    
    log_info "Waiting for $service_name to be ready at $url..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$url" >/dev/null 2>&1; then
            log_success "$service_name is ready!"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    log_error "$service_name failed to start within $((max_attempts * 2)) seconds"
    return 1
}

# Function to cleanup on exit
cleanup() {
    if [ "$1" != "0" ]; then
        log_error "Script failed. Cleaning up..."
        kill_port 8081 "backend"
        kill_port 61234 "frontend"
    fi
}

# Set up cleanup trap
trap 'cleanup $?' EXIT

log_info "Starting Portfolio E2E Environment Setup..."

# Start timing
START_TIME=$(date +%s)

# Step 1: Complete cleanup of existing environment
log_info "Step 1: Complete cleanup of existing environment..."

# Kill processes on specific ports
kill_port 8081 "backend"
kill_port 61234 "frontend"

# Kill any gradle or vite processes
pkill -f "bootRun" 2>/dev/null || true
pkill -f "vite" 2>/dev/null || true

# Stop and remove Docker containers
log_info "Stopping Docker containers..."
docker-compose -f compose.yaml down 2>/dev/null || true

# Clean up Docker resources
log_info "Cleaning up Docker resources..."
docker system prune -f >/dev/null 2>&1 || true

# Remove old log files
log_info "Removing old log files..."
rm -f backend.log frontend.log

log_success "Environment cleanup complete"
sleep 2

# Step 2: Start Docker services (PostgreSQL & Redis)
log_info "Step 2: Starting Docker services (PostgreSQL & Redis)..."
docker-compose -f compose.yaml up -d

# Wait for Docker services to be ready
log_info "Waiting for Docker services to start..."
sleep 10

# Check PostgreSQL
if ! docker-compose -f compose.yaml exec -T postgres-dev pg_isready -U postgres >/dev/null 2>&1; then
    log_warning "PostgreSQL not ready, waiting longer..."
    sleep 10
fi

# Check Redis
if ! docker-compose -f compose.yaml exec -T redis-dev redis-cli ping >/dev/null 2>&1; then
    log_warning "Redis not ready, waiting longer..."
    sleep 5
fi

log_success "Docker services are ready"

# Step 3: Start Backend
log_info "Step 3: Starting Spring Boot backend..."
nohup ./gradlew bootRun > backend.log 2>&1 &
BACKEND_PID=$!

# Step 4: Start Frontend
log_info "Step 4: Starting Vue.js frontend..."
cd ui
nohup npm run dev > ../frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..

# Step 5: Wait for services to be ready
log_info "Step 5: Waiting for services to be ready..."

# Wait for backend (health endpoint)
wait_for_service "http://localhost:8081/actuator/health" "Backend"

# Wait for frontend (main page)
wait_for_service "http://localhost:61234" "Frontend"

# Step 6: Run tests to verify everything works
log_info "Step 6: Running unit tests to verify setup..."
if ./gradlew test >/dev/null 2>&1; then
    log_success "Unit tests passed"
else
    log_error "Unit tests failed"
    exit 1
fi

if [ "$SETUP_ONLY" = false ]; then
    log_info "Step 7: Running E2E tests to verify full stack..."
    if [ "$VERBOSE" = true ]; then
        log_info "Running E2E tests with full output..."
        if E2E=true ./gradlew test --info -Pheadless=true; then
            log_success "E2E tests passed"
        else
            log_error "E2E tests failed"
            exit 1
        fi
    else
        if E2E=true ./gradlew test --info -Pheadless=true >/dev/null 2>&1; then
            log_success "E2E tests passed"
        else
            log_error "E2E tests failed"
            exit 1
        fi
    fi
else
    log_info "Step 7: Skipping E2E tests (--setup mode)"
fi

# Calculate execution time
END_TIME=$(date +%s)
EXECUTION_TIME=$((END_TIME - START_TIME))
MINUTES=$((EXECUTION_TIME / 60))
SECONDS=$((EXECUTION_TIME % 60))

# Success message
log_success "🎉 E2E Environment Setup Complete!"
if [ $MINUTES -gt 0 ]; then
    log_success "⏱️  Total execution time: ${MINUTES}m ${SECONDS}s"
else
    log_success "⏱️  Total execution time: ${SECONDS}s"
fi
echo ""
log_info "Services running:"
log_info "  • PostgreSQL: localhost:5432"
log_info "  • Redis: localhost:6379"
log_info "  • Backend API: http://localhost:8081"
log_info "  • Frontend: http://localhost:61234"
echo ""
if [ "$SETUP_ONLY" = true ]; then
    log_info "To run E2E tests: ./e2e-test.sh --verbose (or manually: export E2E=true && ./gradlew test --info -Pheadless=true)"
fi
if [ "$CLEANUP_AFTER" = false ]; then
    log_info "To stop services: pkill -f 'bootRun|vite' && docker-compose -f compose.yaml down"
fi
echo ""
log_info "Logs:"
log_info "  • Backend: tail -f backend.log"
log_info "  • Frontend: tail -f frontend.log"

# Cleanup after tests if requested
if [ "$CLEANUP_AFTER" = true ]; then
    echo ""
    log_info "Cleaning up services..."
    CLEANUP_START=$(date +%s)
    
    # Kill backend and frontend processes
    kill_port 8081 "backend"
    kill_port 61234 "frontend"
    
    # Stop Docker containers
    docker-compose -f compose.yaml down >/dev/null 2>&1 || true
    
    CLEANUP_END=$(date +%s)
    CLEANUP_TIME=$((CLEANUP_END - CLEANUP_START))
    log_success "Services stopped and cleaned up (${CLEANUP_TIME}s)"
fi