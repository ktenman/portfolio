# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Portfolio Management System** - a full-stack application for tracking investment portfolios with automated price updates and performance calculations.

**Tech Stack:**

- Backend: Kotlin 2.1.21, Spring Boot 3.4.6, Java 21
- Frontend: Vue.js 3.5.16, TypeScript, Vite 6.3.5, Bootstrap 5.3.5
- Database: PostgreSQL 17 with Flyway migrations
- Cache: Redis 8
- Additional Services: Python-based market price tracker (Selenium), Caddy reverse proxy

## Essential Commands

### Backend Development

```bash
# Build and test
./gradlew clean build        # Clean and build the project
./gradlew bootRun           # Run Spring Boot application (port 8081)
./gradlew test              # Run unit tests only

# Run a single test
./gradlew test --tests "ClassName.methodName"

# E2E tests (use test runner for complete setup)
./test-runner.sh --e2e        # Recommended: Full E2E setup + tests
# OR manual E2E (requires environment setup first):
export E2E=true && ./gradlew test --info -Pheadless=true
```

### Frontend Development

```bash
# Setup and run
npm install                 # Install dependencies
npm run dev                 # Start dev server (port 61234)
npm run build              # Production build

# Code quality (ALWAYS run before testing)
npm run lint-format        # Type check, lint and format together (RECOMMENDED)
npm run lint               # Run ESLint only
npm run format             # Format with Prettier only
npm run format:check       # Check formatting only
```

### Docker Development

```bash
# Local development stack
docker-compose -f compose.yaml up -d                    # Start PostgreSQL & Redis only
docker-compose -f docker-compose.local.yml build        # Build all services
docker-compose -f docker-compose.local.yml up -d        # Run full stack

# E2E test environment (RECOMMENDED)
./test-runner.sh --e2e                                 # Setup + run E2E tests (default output + cleanup)
./test-runner.sh --e2e --verbose                       # Setup + run E2E tests (detailed output + cleanup)
./test-runner.sh --e2e --silent                        # Setup + run E2E tests (minimal output + cleanup)
./test-runner.sh --e2e --keep                          # Setup + run E2E tests (keep services running)
./test-runner.sh --setup                               # Setup environment only (no test execution)

# Manual E2E test environment (if needed)
docker-compose -f compose.yaml down
./test-runner.sh --setup
export E2E=true && ./gradlew test --info -Pheadless=true

# Stop all services
pkill -f 'bootRun|vite' && docker-compose -f compose.yaml down
```

## Architecture Overview

The system follows a microservices architecture with these key components:

1. **API Gateway (Caddy)** - Handles authentication and reverse proxy at `/`
2. **Auth Service** - OAuth 2.0 with Google/GitHub providers
3. **Frontend (Vue.js SPA)** - User interface served at `/ui`
4. **Backend API (Spring Boot)** - Business logic at `/api`
5. **PostgreSQL** - Primary data store with Flyway migrations
6. **Redis** - Caching layer for performance
7. **Market Price Tracker** - Python/Selenium service for web scraping

### Key Architectural Patterns

- **Repository Pattern**: Data access through Spring Data JPA repositories
- **Service Layer**: Business logic in service classes with `@Transactional` boundaries
- **Caching**: Redis with Spring Cache annotations (`@Cacheable`, `@CacheEvict`)
- **Scheduled Jobs**: Background tasks for price updates and XIRR calculations
- **Aspect-Oriented Logging**: `@Loggable` annotation for method-level logging
- **Integration Testing**: Uses Testcontainers for PostgreSQL and Redis
- **E2E Testing**: Selenide-based browser tests with retry mechanism

### Database Schema

Key entities:

- `Instrument` - Financial instruments (stocks, ETFs, crypto)
- `PortfolioTransaction` - Buy/sell transactions
- `DailyPrice` - Historical price data
- `PortfolioDailySummary` - Daily performance snapshots
- `JobExecution` - Background job tracking

Migrations are in `src/main/resources/db/migration/` using Flyway naming convention.

### External Integrations

- **Alpha Vantage API** - Stock/ETF price data (requires API key)
- **Binance API** - Cryptocurrency prices
- **Financial Times API** - Additional market data
- **Google Cloud Vision** - OCR for captcha solving
- **Telegram Bot API** - Notifications

### Testing Strategy

1. **Unit Tests**: Mock external dependencies with Mockito
2. **Integration Tests**: Use `@IntegrationTest` annotation which starts PostgreSQL and Redis containers
3. **E2E Tests**: Browser-based tests with Selenide, include retry mechanism for flaky tests
4. **API Testing**: WireMock for external API mocking

### Configuration

- Backend config: `src/main/resources/application.yml`
- Frontend env: Development uses proxy config in `vite.config.ts`
- Docker services: Multiple compose files for different environments
- CI/CD: GitHub Actions workflows in `.github/workflows/`

### Test Runner Script

#### Unified Test Runner (`test-runner.sh`)

A comprehensive test runner that combines unit tests, E2E tests, and environment setup. This script replaces the previous separate `e2e-test.sh` and provides all testing functionality in one place.

**Features:**

- Runs unit tests and E2E tests separately or together
- Automatically sets up the E2E environment when needed
- Parses HTML test reports for accurate results
- Shows formatted summary with colors and test statistics
- Offers to clean up services after tests
- Multiple modes for different testing scenarios
- Combines functionality from both test-runner.sh and e2e-test.sh

**Usage:**

```bash
./test-runner.sh              # Run all tests with verbose output (default)
./test-runner.sh --unit       # Run only unit tests
./test-runner.sh --e2e        # Run only E2E tests with environment setup
./test-runner.sh --summary    # Show summary of existing test results
./test-runner.sh --setup      # Setup E2E environment only (no tests)
./test-runner.sh --keep       # Keep services running after tests
./test-runner.sh --silent     # Minimal output mode
./test-runner.sh --verbose    # Show detailed output (explicit verbose)
./test-runner.sh --parallel   # Run tests with optimized parallel execution
./test-runner.sh --help       # Show help message
```

**Output:** The script displays a comprehensive test summary with:

- Separate E2E and Unit test statistics
- Total tests, passed, failed, and ignored counts
- Test duration and success rates
- Specific failed test names when applicable
- Overall execution time
- Detailed E2E test class results when running E2E tests

### Development Tips

- Use `@IntegrationTest` annotation for tests requiring database/Redis
- Frontend API calls go through `/api` proxy in development
- Redis cache keys are defined in `ui/constants/cache-keys.ts`
- Scheduled jobs can be disabled with `scheduling.enabled=false`
- E2E tests generate screenshots on failure (check build artifacts)
- Use `./test-runner.sh --e2e` for reliable E2E testing

# important-instruction-reminders

Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (\*.md) or README files. Only create documentation files if explicitly requested by the User.
NEVER add comments to code unless explicitly requested by the User. Keep code clean without documentation comments.
