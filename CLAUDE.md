# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Portfolio Management System** - a full-stack application for tracking investment portfolios with automated price updates and performance calculations.

**Tech Stack:**

- Backend: Kotlin 2.1.21, Spring Boot 3.5.0, Java 21
- Frontend: Vue.js 3.5.16, TypeScript, Vite 6.3.5, Bootstrap 5.3.5
- Database: PostgreSQL 17 with Flyway migrations
- Cache: Redis 8
- Authentication: Keycloak 25 + OAuth2-Proxy
- Additional Services: Python-based market price tracker (Selenium), Caddy reverse proxy

## Essential Commands

### Backend Development

```bash
# Suppress Java 21 warnings (run this once per terminal session)
export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED"

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

#### Java 21 Native Access Warnings

If you see warnings like "WARNING: A restricted method in java.lang.System has been called", set this environment variable:

```bash
export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED"
```

Add this to your shell profile (~/.bashrc, ~/.zshrc) to make it permanent.

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

# Testing
npm test                   # Run all UI tests
npm test -- --run          # Run tests once (no watch mode)
npm test -- --coverage     # Run tests with coverage report
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

1. **API Gateway (Caddy)** - Reverse proxy at `/`
2. **Auth Service** - Keycloak with Google OAuth provider + OAuth2-Proxy for session management
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
5. **Frontend Tests**: Vue Test Utils with Vitest, comprehensive coverage of business logic
6. **Business Logic Focus**: Tests prioritize business logic over framework functionality

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
- Frontend tests focus on business logic with comprehensive coverage
- Test files excluded from coverage: `.eslintrc.cjs` and `app.vue`
- **ALWAYS run `npm run lint-format` after making changes to UI code** - This ensures type safety, linting, and code formatting
- **ALWAYS run `npm test` after making changes to UI code** - This ensures all tests pass and functionality is not broken

### Code Style Guidelines

#### No Comments Policy

This codebase enforces a **strict no-comments policy** using `eslint-plugin-no-comments`. Write self-documenting code with:

- Clear, descriptive variable and function names
- Small, focused functions that do one thing well
- Meaningful type definitions and interfaces
- Well-structured code that expresses intent clearly

The only exception is TypeScript triple-slash directives (`///`) when required for type definitions.

### Code Quality Tools

#### Knip - Unused Code Detection

The project uses [Knip](https://knip.dev/) to detect unused exports, dependencies, and files. Configuration is in `knip.json`.

**Running Knip:**

```bash
npm run check-unused       # Check for unused code
npm run check-unused:fix   # Auto-fix some issues
```

**Configuration Notes:**

- Configured for Vue 3 + TypeScript with `vue-tsc` compiler
- Automatically detects Vue and Vite plugins
- Entry points: `ui/main.ts` and `ui/index.html`
- Ignores backend code (`src/**`), build outputs, and infrastructure files

**Known Limitations:**

- May not detect unused object properties in service exports (e.g., individual methods in service objects)
- For comprehensive unused code detection, consider manual review of service methods

# important-instruction-reminders

Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (\*.md) or README files. Only create documentation files if explicitly requested by the User.
NEVER add comments to code. Follow clean code principles where code is self-documenting through clear naming and structure.
AVOID all forms of code comments including:

- Single-line comments (//)
- Multi-line comments (/\* \*/)
- Documentation comments (/\*\* \*/)
- Inline comments
  The only exception is TypeScript triple-slash directives (///) which are required for type definitions.

## File Naming Conventions

ALWAYS follow the existing file naming patterns in the codebase:

- Use kebab-case for all file names (e.g., `transaction-form.vue`, `use-crud-alerts.ts`)
- NEVER add suffixes like `-improved`, `-new`, `-simple`, `-refactored` to file names
- When updating a file, modify it in place rather than creating a new version
- Component files: `[feature-name].vue` (e.g., `instrument-table.vue`)
- Composables: `use-[feature].ts` (e.g., `use-form-validation.ts`)
- Services: `[domain]-service.ts` (e.g., `instruments-service.ts`)
- Models/Types: `[entity].ts` (e.g., `instrument.ts`)
- Keep file names consistent with the existing patterns in each directory
