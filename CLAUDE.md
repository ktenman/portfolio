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

# E2E tests (requires docker-compose.e2e.yml running)
export E2E=true && ./gradlew test --info -Pheadless=true
```

### Frontend Development

```bash
# Setup and run
npm install                 # Install dependencies
npm run dev                 # Start dev server (port 61234)
npm run build              # Production build

# Code quality
npm run lint               # Run ESLint
npm run format             # Format with Prettier
npm run format:check       # Check formatting
npm run lint-format        # Lint and format together
```

### Docker Development

```bash
# Local development stack
docker-compose -f compose.yaml up -d                    # Start PostgreSQL & Redis only
docker-compose -f docker-compose.local.yml build        # Build all services
docker-compose -f docker-compose.local.yml up -d        # Run full stack

# E2E test environment (RECOMMENDED)
./e2e-test.sh                                          # Setup + run E2E tests (verbose + cleanup) - DEFAULT
./e2e-test.sh --silent                                 # Setup + run E2E tests (silent + cleanup)
./e2e-test.sh --keep                                   # Setup + run E2E tests (verbose, no cleanup)
./e2e-test.sh --setup                                  # Setup only (no E2E test execution)

# Manual E2E test environment (if needed)
docker-compose -f docker-compose.yml -f docker-compose.e2e.yml down
docker volume rm portfolio_postgres_data_e2e
docker-compose -f docker-compose.yml -f docker-compose.e2e.yml up -d && sleep 30
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

### E2E Test Runner

The `e2e-test.sh` script provides a bulletproof, one-command solution for running E2E tests:

**Features:**
- Automatically kills existing processes on ports 8081 and 61234
- Starts PostgreSQL and Redis containers
- Launches Spring Boot backend and Vue.js frontend
- Waits for all services to be ready
- Runs unit tests and E2E tests to verify setup
- Provides colored output and helpful status messages

**Usage:**
```bash
./e2e-test.sh                     # Setup + run E2E tests (verbose + cleanup) - DEFAULT
./e2e-test.sh --silent            # Setup + run E2E tests (silent + cleanup)
./e2e-test.sh --keep              # Setup + run E2E tests (verbose, no cleanup)  
./e2e-test.sh --setup             # Setup only (no E2E test execution)
```

**Output:** The script provides real-time status updates and completes with service URLs and helpful commands for monitoring and cleanup.

### Development Tips

- Use `@IntegrationTest` annotation for tests requiring database/Redis
- Frontend API calls go through `/api` proxy in development
- Redis cache keys are defined in `ui/constants/cache-keys.ts`
- Scheduled jobs can be disabled with `scheduling.enabled=false`
- E2E tests generate screenshots on failure (check build artifacts)
- Use `./e2e-test.sh` for reliable E2E testing
