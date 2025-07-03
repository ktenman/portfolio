#!/bin/bash

# Test Runner Script - Unified testing solution for Portfolio application
# Combines functionality from both test-runner.sh and e2e-test.sh
# Usage: ./test-runner.sh [OPTIONS]

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
RUN_UNIT=true
RUN_E2E=true
SETUP_ONLY=false
KEEP_SERVICES=false
SILENT_MODE=false
SUMMARY_ONLY=false
PARALLEL_MODE=false
VERBOSE_MODE=true

# Global variables for test results
UNIT_TOTAL=0
UNIT_PASSED=0
UNIT_FAILED=0
UNIT_IGNORED=0
UNIT_DURATION="0s"
UNIT_SUCCESS_RATE="N/A"

E2E_TOTAL=0
E2E_PASSED=0
E2E_FAILED=0
E2E_IGNORED=0
E2E_DURATION="0s"
E2E_SUCCESS_RATE="N/A"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --unit)
            RUN_UNIT=true
            RUN_E2E=false
            shift
            ;;
        --e2e)
            RUN_UNIT=false
            RUN_E2E=true
            shift
            ;;
        --all)
            RUN_UNIT=true
            RUN_E2E=true
            shift
            ;;
        --summary)
            SUMMARY_ONLY=true
            shift
            ;;
        --setup)
            SETUP_ONLY=true
            RUN_UNIT=false
            shift
            ;;
        --keep)
            KEEP_SERVICES=true
            shift
            ;;
        --silent)
            SILENT_MODE=true
            VERBOSE_MODE=false
            shift
            ;;
        --verbose)
            VERBOSE_MODE=true
            SILENT_MODE=false
            shift
            ;;
        --parallel)
            PARALLEL_MODE=true
            shift
            ;;
        --help)
            echo "Test Runner Script - Unified testing solution"
            echo ""
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --unit        Run only unit tests"
            echo "  --e2e         Run only E2E tests (includes environment setup)"
            echo "  --all         Run all tests (default)"
            echo "  --parallel    Run tests with optimized parallel execution"
            echo "  --summary     Show summary of existing test results"
            echo "  --setup       Setup E2E environment only (no tests)"
            echo "  --keep        Keep services running after tests"
            echo "  --silent      Minimal output"
            echo "  --verbose     Show detailed output (opposite of --silent)"
            echo "  --help        Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                    # Run all tests with summary"
            echo "  $0 --parallel         # Run tests with parallel optimization"
            echo "  $0 --unit             # Run only unit tests"
            echo "  $0 --e2e --keep       # Run E2E tests and keep services"
            echo "  $0 --e2e --verbose    # Run E2E tests with detailed output"
            echo "  $0 --setup            # Setup E2E environment only"
            echo "  $0 --summary          # Show test results summary only"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Utility functions
print_info() {
    if [ "$SILENT_MODE" = false ]; then
        echo -e "${BLUE}[INFO]${NC} $1"
    fi
}

print_success() {
    if [ "$SILENT_MODE" = false ]; then
        echo -e "${GREEN}[SUCCESS]${NC} $1"
    fi
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to kill process on port
kill_port() {
    local port=$1
    local pid=$(lsof -ti:$port)
    if [ ! -z "$pid" ]; then
        kill -9 $pid 2>/dev/null
        sleep 1
    fi
}

# Function to extract failed test names from HTML report
extract_failed_tests() {
    local report_file=$1
    local failed_tests=""
    
    if [ -f "$report_file" ]; then
        # Extract failed test names more simply
        failed_tests=$(grep -A 5 '<h2>Failed tests</h2>' "$report_file" 2>/dev/null | \
                      grep -E '<a href="classes/.*\.html#.*">' | \
                      sed -E 's/.*>([^<]*)<\/a>.*/\1/' | \
                      sed 's/&gt;/>/g' | sed 's/&lt;/</g' | sed 's/&amp;/\&/g')
    fi
    
    echo "$failed_tests"
}

# Function to parse test results and set global variables
parse_test_results() {
    local report_file=$1
    local test_type=$2
    
    if [ ! -f "$report_file" ]; then
        if [ "$test_type" = "unit" ]; then
            UNIT_TOTAL=0; UNIT_PASSED=0; UNIT_FAILED=0; UNIT_IGNORED=0; UNIT_DURATION="0s"; UNIT_SUCCESS_RATE="N/A"
        else
            E2E_TOTAL=0; E2E_PASSED=0; E2E_FAILED=0; E2E_IGNORED=0; E2E_DURATION="0s"; E2E_SUCCESS_RATE="N/A"
        fi
        return
    fi
    
    # Extract values from HTML
    local total=$(grep -A1 'id="tests"' "$report_file" | grep '<div class="counter">' | sed 's/.*>\([0-9]*\)<.*/\1/' | head -1)
    local failures=$(grep -A1 'id="failures"' "$report_file" | grep '<div class="counter">' | sed 's/.*>\([0-9]*\)<.*/\1/' | head -1)
    local ignored=$(grep -A1 'id="ignored"' "$report_file" | grep '<div class="counter">' | sed 's/.*>\([0-9]*\)<.*/\1/' | head -1)
    local duration=$(grep -A1 'id="duration"' "$report_file" | grep '<div class="counter">' | sed 's/.*>\([^<]*\)<.*/\1/' | head -1)
    local success_rate=$(grep -A1 'id="successRate"' "$report_file" | grep '<div class="percent">' | sed 's/.*>\([^<]*\)<.*/\1/' | head -1)
    
    # Set defaults if empty
    total=${total:-0}
    failures=${failures:-0}
    ignored=${ignored:-0}
    duration=${duration:-"0s"}
    success_rate=${success_rate:-"N/A"}
    
    local passed=$((total - failures - ignored))
    
    # Check if this is E2E or Unit test report
    if grep -q "e2e</a>" "$report_file" 2>/dev/null; then
        # This is an E2E report
        E2E_TOTAL=$total
        E2E_PASSED=$passed
        E2E_FAILED=$failures
        E2E_IGNORED=$ignored
        E2E_DURATION=$duration
        E2E_SUCCESS_RATE=$success_rate
        
        # If this was supposed to be unit tests, it means we have combined results
        if [ "$test_type" = "unit" ]; then
            UNIT_TOTAL=0; UNIT_PASSED=0; UNIT_FAILED=0; UNIT_IGNORED=0; UNIT_DURATION="0s"; UNIT_SUCCESS_RATE="N/A"
        fi
    else
        # This is a unit test report
        UNIT_TOTAL=$total
        UNIT_PASSED=$passed
        UNIT_FAILED=$failures
        UNIT_IGNORED=$ignored
        UNIT_DURATION=$duration
        UNIT_SUCCESS_RATE=$success_rate
    fi
}

# Function to display test summary
display_test_summary() {
    echo ""
    echo -e "${RED}⏺${NC} ${GREEN}Test Results Summary${NC}"
    
    echo ""
    echo -e "${BLUE}  E2E Tests:${NC}"
    echo "  - Total tests: $E2E_TOTAL"
    echo "  - Passed: $E2E_PASSED"
    echo "  - Failed: $E2E_FAILED"
    if [ "$E2E_IGNORED" -gt 0 ] 2>/dev/null; then
        echo "  - Ignored: $E2E_IGNORED"
    fi
    echo "  - Duration: $E2E_DURATION"
    echo "  - Success rate: $E2E_SUCCESS_RATE"
    
    echo ""
    echo -e "${BLUE}  Unit Tests:${NC}"
    echo "  - Total tests: $UNIT_TOTAL"
    echo "  - Passed: $UNIT_PASSED"
    echo "  - Failed: $UNIT_FAILED"
    if [ "$UNIT_IGNORED" -gt 0 ] 2>/dev/null; then
        echo "  - Ignored: $UNIT_IGNORED"
    fi
    echo "  - Duration: $UNIT_DURATION"
    echo "  - Success rate: $UNIT_SUCCESS_RATE"
    echo ""
}

# Function to wait for service to be ready
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=${3:-30}
    local sleep_interval=${4:-2}
    
    if [ "$SILENT_MODE" = false ]; then
        print_info "Waiting for $service_name to be ready at $url..."
    fi
    
    for ((i=1; i<=max_attempts; i++)); do
        if curl -s "$url" >/dev/null 2>&1; then
            if [ "$SILENT_MODE" = false ]; then
                print_success "$service_name is ready!"
            fi
            return 0
        fi
        
        if [ "$SILENT_MODE" = false ] && [ $((i % 5)) -eq 0 ]; then
            echo -n "."
        fi
        sleep $sleep_interval
    done
    
    print_error "$service_name failed to start within $((max_attempts * sleep_interval)) seconds"
    return 1
}

# Function to setup E2E environment
setup_e2e_environment() {
    print_info "Setting up E2E test environment..."
    local start_time=$(date +%s)
    
    # Step 1: Complete cleanup
    print_info "Step 1: Complete cleanup of existing environment..."
    
    # Kill processes on specific ports
    if [ "$SILENT_MODE" = false ]; then
        print_info "Stopping any existing processes on ports 8081 and 61234..."
    fi
    kill_port 8081
    kill_port 61234
    
    # Stop any running gradle/vite processes
    pkill -f 'gradle.*bootRun' 2>/dev/null || true
    pkill -f 'bootRun' 2>/dev/null || true
    pkill -f 'vite' 2>/dev/null || true
    
    # Docker cleanup
    if [ "$SILENT_MODE" = false ]; then
        print_info "Stopping Docker containers..."
    fi
    docker-compose -f docker-compose.e2e-minimal.yml down 2>/dev/null || true
    docker-compose -f docker-compose.local.yml down 2>/dev/null || true
    docker-compose -f compose.yaml down 2>/dev/null || true
    
    # Clean up Docker resources
    if [ "$SILENT_MODE" = false ]; then
        print_info "Cleaning up Docker resources..."
    fi
    docker volume rm portfolio_postgres_data_e2e 2>/dev/null || true
    docker system prune -f >/dev/null 2>&1 || true
    
    # Remove old log files
    if [ "$SILENT_MODE" = false ]; then
        print_info "Removing old log files..."
    fi
    rm -f backend.log frontend.log backend-test.log frontend-test.log
    
    print_success "Environment cleanup complete"
    sleep 2
    
    # Step 2: Start Docker services
    print_info "Step 2: Starting Docker services (PostgreSQL & Redis)..."
    docker-compose -f compose.yaml up -d
    
    if [ "$SILENT_MODE" = false ]; then
        print_info "Waiting for Docker services to start..."
    fi
    sleep 10
    
    # Verify PostgreSQL is ready
    for i in {1..30}; do
        if docker-compose -f compose.yaml exec -T postgres-dev pg_isready -U postgres > /dev/null 2>&1; then
            if [ "$SILENT_MODE" = false ]; then
                print_success "PostgreSQL is ready"
            fi
            break
        fi
        [ $i -eq 30 ] && print_error "PostgreSQL failed to start" && return 1
        sleep 1
    done
    
    # Verify Redis is ready
    for i in {1..30}; do
        if docker-compose -f compose.yaml exec -T redis-dev redis-cli ping > /dev/null 2>&1; then
            if [ "$SILENT_MODE" = false ]; then
                print_success "Redis is ready"
            fi
            break
        fi
        [ $i -eq 30 ] && print_error "Redis failed to start" && return 1
        sleep 1
    done
    
    print_success "Docker services are ready"
    
    # Step 3: Start Spring Boot backend
    print_info "Step 3: Starting Spring Boot backend..."
    if [ "$VERBOSE_MODE" = true ]; then
        ./gradlew bootRun &
    else
        ./gradlew bootRun > backend.log 2>&1 &
    fi
    BACKEND_PID=$!
    
    # Step 4: Start Vue.js frontend
    print_info "Step 4: Starting Vue.js frontend..."
    if [ "$VERBOSE_MODE" = true ]; then
        npm run dev &
    else
        npm run dev > frontend.log 2>&1 &
    fi
    FRONTEND_PID=$!
    
    # Step 5: Wait for services
    print_info "Step 5: Waiting for services to be ready..."
    
    # Wait for backend health endpoint
    wait_for_service "http://localhost:8081/actuator/health" "Backend" 60 2
    if [ $? -ne 0 ]; then
        return 1
    fi
    
    # Wait for frontend
    wait_for_service "http://localhost:61234" "Frontend" 30 2
    if [ $? -ne 0 ]; then
        return 1
    fi
    
    # Verify backend health
    if ! curl -s http://localhost:8081/actuator/health | grep -q "UP" 2>/dev/null; then
        print_error "Backend health check failed"
        return 1
    fi
    
    local end_time=$(date +%s)
    local setup_time=$((end_time - start_time))
    print_success "E2E environment setup completed in ${setup_time}s"
    
    return 0
}

# Function to run unit tests
run_unit_tests() {
    print_info "Running unit tests..."
    
    # Clean unit test results
    rm -rf build/reports/tests/test
    
    # Make sure E2E is not set
    unset E2E
    
    # Run all tests but E2E tests will be skipped without E2E environment variable
    if [ "$SILENT_MODE" = false ]; then
        ./gradlew test
    else
        ./gradlew test > /dev/null 2>&1
    fi
    
    local status=$?
    
    if [ $status -eq 0 ]; then
        print_success "Unit tests completed"
    else
        print_error "Unit tests failed"
    fi
    
    # Copy unit test results to separate directory
    if [ -d "build/reports/tests/test" ]; then
        mkdir -p build/reports/tests/unit
        cp -r build/reports/tests/test/* build/reports/tests/unit/
    fi
    
    # Parse test results
    local unit_test_report="build/reports/tests/unit/index.html"
    parse_test_results "$unit_test_report" "unit"
    
    return $status
}

# Function to run E2E tests
run_e2e_tests() {
    print_info "Running E2E tests..."
    
    # Clean E2E test results
    rm -rf build/reports/tests/test
    
    # Set E2E environment variable
    export E2E=true
    
    # Create temporary file for test output
    local test_output=$(mktemp)
    local test_failed=false
    
    # Run E2E tests with appropriate output mode
    if [ "$VERBOSE_MODE" = true ] || [ "$SILENT_MODE" = false ]; then
        if ./gradlew test --info -Pheadless=true 2>&1 | tee "$test_output"; then
            print_success "E2E tests completed"
        else
            print_error "E2E tests failed"
            test_failed=true
        fi
    else
        if ./gradlew test --info -Pheadless=true > "$test_output" 2>&1; then
            print_success "E2E tests completed"
        else
            print_error "E2E tests failed"
            test_failed=true
        fi
    fi
    
    # Extract and display detailed test results
    if [ "$SILENT_MODE" = false ]; then
        echo ""
        print_info "📊 E2E Test Results Summary:"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        
        # Extract test statistics
        local tests_run=$(grep -oE "[0-9]+ tests?" "$test_output" | grep -oE "[0-9]+" | tail -1 || echo "0")
        local tests_passed=$(grep -oE "[0-9]+ passed" "$test_output" | grep -oE "[0-9]+" | tail -1 || echo "0")
        local tests_failed=$(grep -oE "[0-9]+ failed" "$test_output" | grep -oE "[0-9]+" | tail -1 || echo "0")
        local tests_skipped=$(grep -oE "[0-9]+ skipped" "$test_output" | grep -oE "[0-9]+" | tail -1 || echo "0")
        
        # Show individual test class results
        if grep -q "InstrumentManagementE2ETests" "$test_output"; then
            echo ""
            print_info "Test Class: InstrumentManagementE2ETests"
            grep -A5 "InstrumentManagementE2ETests" "$test_output" | grep -E "PASSED|FAILED|SKIPPED" | sed 's/^/  /' || true
        fi
        
        if grep -q "TransactionManagementE2ETests" "$test_output"; then
            echo ""
            print_info "Test Class: TransactionManagementE2ETests"
            grep -A5 "TransactionManagementE2ETests" "$test_output" | grep -E "PASSED|FAILED|SKIPPED" | sed 's/^/  /' || true
        fi
        
        # Show summary
        echo ""
        print_info "Overall E2E Statistics:"
        echo "  • Total tests run: $tests_run"
        if [ "$tests_passed" -gt 0 ]; then
            echo -e "  • ${GREEN}Tests passed: $tests_passed${NC}"
        fi
        if [ "$tests_failed" -gt 0 ]; then
            echo -e "  • ${RED}Tests failed: $tests_failed${NC}"
        fi
        if [ "$tests_skipped" -gt 0 ]; then
            echo -e "  • ${YELLOW}Tests skipped: $tests_skipped${NC}"
        fi
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        
        # Show failure details if tests failed and not in verbose mode
        if [ "$test_failed" = true ] && [ "$VERBOSE_MODE" = false ]; then
            echo ""
            print_error "Showing failed test details:"
            grep -A20 -B5 "FAILED" "$test_output" | grep -v "BUILD FAILED" || true
        fi
    fi
    
    # Clean up temp file
    rm -f "$test_output"
    
    # Copy E2E test results to separate directory
    if [ -d "build/reports/tests/test" ]; then
        mkdir -p build/reports/tests/e2e
        cp -r build/reports/tests/test/* build/reports/tests/e2e/
    fi
    
    # Parse test results
    local e2e_test_report="build/reports/tests/e2e/index.html"
    parse_test_results "$e2e_test_report" "e2e"
    
    if [ "$test_failed" = true ]; then
        return 1
    fi
    
    return 0
}

# Function to cleanup services
cleanup_services() {
    local cleanup_start=$(date +%s)
    
    print_info "Stopping test services..."
    
    # Kill backend and frontend if we started them
    [ ! -z "$BACKEND_PID" ] && kill $BACKEND_PID 2>/dev/null
    [ ! -z "$FRONTEND_PID" ] && kill $FRONTEND_PID 2>/dev/null
    
    # Kill by port as backup
    kill_port 8081
    kill_port 61234
    
    # Stop Docker containers
    docker-compose -f compose.yaml down > /dev/null 2>&1
    
    # Clean up log files
    rm -f backend.log frontend.log backend-test.log frontend-test.log
    
    local cleanup_end=$(date +%s)
    local cleanup_time=$((cleanup_end - cleanup_start))
    
    print_success "Services stopped and cleaned up (${cleanup_time}s)"
}

# Main execution
main() {
    # Start timer
    start_time=$(date +%s)
    
    # Header
    if [ "$SILENT_MODE" = false ]; then
        echo ""
        print_info "Starting Portfolio Test Runner..."
        echo ""
    fi
    
    # Summary only mode
    if [ "$SUMMARY_ONLY" = true ]; then
        # Try to parse both unit and E2E reports
        local unit_report="build/reports/tests/unit/index.html"
        local e2e_report="build/reports/tests/e2e/index.html"
        local generic_report="build/reports/tests/test/index.html"
        
        if [ -f "$unit_report" ]; then
            parse_test_results "$unit_report" "unit"
        fi
        
        if [ -f "$e2e_report" ]; then
            parse_test_results "$e2e_report" "e2e"
        elif [ -f "$generic_report" ]; then
            parse_test_results "$generic_report" "e2e"
        fi
        
        display_test_summary
        exit 0
    fi
    
    # Track test results
    local unit_test_status=0
    local e2e_test_status=0
    
    # Parallel execution mode
    if [ "$PARALLEL_MODE" = true ] && [ "$RUN_UNIT" = true ] && [ "$RUN_E2E" = true ] && [ "$SETUP_ONLY" = false ]; then
        print_info "Running tests in parallel mode..."
        
        # Start E2E environment setup in background
        print_info "Starting E2E environment setup in background..."
        (
            setup_e2e_environment > /tmp/e2e_setup.log 2>&1
            echo $? > /tmp/e2e_setup_status
        ) &
        local setup_pid=$!
        
        # Run unit tests while E2E environment is setting up
        print_info "Running unit tests while E2E environment sets up..."
        run_unit_tests
        unit_test_status=$?
        
        # Wait for E2E environment setup to complete
        print_info "Waiting for E2E environment setup to complete..."
        wait $setup_pid
        local setup_status=$(cat /tmp/e2e_setup_status 2>/dev/null || echo "1")
        
        if [ "$setup_status" -ne 0 ]; then
            print_error "Failed to setup E2E environment"
            cat /tmp/e2e_setup.log 2>/dev/null
            rm -f /tmp/e2e_setup.log /tmp/e2e_setup_status
            exit 1
        fi
        
        print_success "E2E environment setup completed"
        rm -f /tmp/e2e_setup.log /tmp/e2e_setup_status
        
        # Run E2E tests
        run_e2e_tests
        e2e_test_status=$?
        
    else
        # Sequential execution mode (default)
        
        # Run unit tests first (if requested)
        if [ "$RUN_UNIT" = true ] && [ "$SETUP_ONLY" = false ]; then
            run_unit_tests
            unit_test_status=$?
        fi
        
        # Setup E2E environment if needed
        if [ "$RUN_E2E" = true ] || [ "$SETUP_ONLY" = true ]; then
            setup_e2e_environment
            if [ $? -ne 0 ]; then
                print_error "Failed to setup E2E environment"
                exit 1
            fi
            
            if [ "$SETUP_ONLY" = true ]; then
                print_success "🎉 E2E Environment Setup Complete!"
                echo ""
                echo -e "${GREEN}Services are running at:${NC}"
                echo "  Backend API:     http://localhost:8081"
                echo "  Frontend UI:     http://localhost:61234"
                echo "  Backend Health:  http://localhost:8081/actuator/health"
                echo ""
                exit 0
            fi
        fi
        
        # Run E2E tests (if requested)
        if [ "$RUN_E2E" = true ]; then
            run_e2e_tests
            e2e_test_status=$?
        fi
    fi
    
    # Calculate execution time
    end_time=$(date +%s)
    total_time=$((end_time - start_time))
    
    # Display test summary
    display_test_summary
    
    # Overall status
    echo -e "${GREEN}Overall Execution:${NC}"
    echo "  ⏱️  Total execution time: ${total_time}s"
    echo ""
    
    # Check both exit codes and parsed failure counts
    local has_failures=false
    if [ $unit_test_status -ne 0 ] || [ $e2e_test_status -ne 0 ] || [ "$UNIT_FAILED" -gt 0 ] 2>/dev/null || [ "$E2E_FAILED" -gt 0 ] 2>/dev/null; then
        has_failures=true
    fi
    
    if [ "$has_failures" = false ]; then
        print_success "🎉 All tests passed!"
    else
        print_error "❌ Some tests failed"
        if [ $unit_test_status -ne 0 ] || [ "$UNIT_FAILED" -gt 0 ] 2>/dev/null; then
            echo "  - Unit tests failed (exit code: $unit_test_status, failures: ${UNIT_FAILED:-0})"
            # Show failed unit tests immediately
            local unit_failed=$(extract_failed_tests "build/reports/tests/unit/index.html")
            if [ -n "$unit_failed" ]; then
                echo ""
                echo -e "${RED}    Failed tests:${NC}"
                echo "$unit_failed" | while IFS= read -r test; do
                    echo "      ❌ $test"
                done
            fi
        fi
        if [ $e2e_test_status -ne 0 ] || [ "$E2E_FAILED" -gt 0 ] 2>/dev/null; then
            echo "  - E2E tests failed (exit code: $e2e_test_status, failures: ${E2E_FAILED:-0})"
            # Show failed E2E tests immediately
            local e2e_failed=$(extract_failed_tests "build/reports/tests/e2e/index.html")
            if [ -n "$e2e_failed" ]; then
                echo ""
                echo -e "${RED}    Failed tests:${NC}"
                echo "$e2e_failed" | while IFS= read -r test; do
                    echo "      ❌ $test"
                done
            fi
        fi
    fi
    
    # Cleanup (automatic - no prompt)
    if [ "$KEEP_SERVICES" = false ] && [ "$RUN_E2E" = true ]; then
        echo ""
        cleanup_services
    elif [ "$KEEP_SERVICES" = true ]; then
        echo ""
        print_info "Services kept running (--keep option used)"
        echo ""
        echo -e "${GREEN}Services are running at:${NC}"
        echo "  Backend:  http://localhost:8081"
        echo "  Frontend: http://localhost:61234"
    fi
    
    echo ""
    
    # Exit with appropriate status
    local exit_code=0
    if [ $unit_test_status -ne 0 ] || [ $e2e_test_status -ne 0 ]; then
        exit_code=1
    fi
    
    # Also check for test failures in parsed results and show failed test names
    if [ "$UNIT_FAILED" -gt 0 ] 2>/dev/null || [ "$E2E_FAILED" -gt 0 ] 2>/dev/null; then
        exit_code=1
        echo ""
        if [ "$UNIT_FAILED" -gt 0 ] 2>/dev/null; then
            print_error "❌ Unit tests have $UNIT_FAILED failures"
            local unit_failed_tests=$(extract_failed_tests "build/reports/tests/unit/index.html")
            if [ -n "$unit_failed_tests" ]; then
                echo -e "${RED}Failed Unit Tests:${NC}"
                echo "$unit_failed_tests" | while IFS= read -r test; do
                    echo "  ❌ $test"
                done
            fi
        fi
        if [ "$E2E_FAILED" -gt 0 ] 2>/dev/null; then
            print_error "❌ E2E tests have $E2E_FAILED failures"
            local e2e_failed_tests=$(extract_failed_tests "build/reports/tests/e2e/index.html")
            if [ -n "$e2e_failed_tests" ]; then
                echo -e "${RED}Failed E2E Tests:${NC}"
                echo "$e2e_failed_tests" | while IFS= read -r test; do
                    echo "  ❌ $test"
                done
            fi
        fi
    fi
    
    exit $exit_code
}

# Run main function
main