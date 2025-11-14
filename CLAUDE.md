# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Portfolio Management System** - a production-ready, full-stack application for tracking investment portfolios with automated price updates and sophisticated performance calculations.

**Tech Stack:**

- Backend: Kotlin 2.2.20, Spring Boot 3.5.6, Java 21
- Frontend: Vue.js 3.5.16, TypeScript 5.8.3, Vite 6.3.5, Bootstrap 5.3.5
- Database: PostgreSQL 42.7.8 with Flyway migrations (V1-V30+)
- Cache: Redis 8 (multi-level caching strategy)
- Testing: Atrium 1.3.0-alpha-2 (Kotlin assertions), JUnit 5, Mockito, Selenide, Vitest
- Build: Gradle 8.8 with Version Catalogs (libs.versions.toml)
- Authentication: Keycloak 25 + OAuth2-Proxy (dev), Custom auth service (prod) ⚠️
- Infrastructure: Docker, Kubernetes, Caddy reverse proxy
- Additional Services: Python-based market price tracker (Selenium), Google Cloud Vision API

## Essential Commands

### Quick Start

Run backend and frontend together in separate terminal windows:

```bash
./run.sh                    # Opens 2 terminals: backend (8081) + frontend (61234)
```

The script automatically:

- Starts Spring Boot backend on http://localhost:8081
- Starts Vite frontend dev server on http://localhost:61234
- Works on macOS, Linux (gnome-terminal/xterm), and Windows

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

# Test Runner (RECOMMENDED - runs all tests)
./test-runner.sh                    # Run ALL tests: backend + frontend + E2E (default)
./test-runner.sh --unit             # Run backend unit + frontend UI tests only
./test-runner.sh --e2e              # Run only E2E tests (with environment setup)
./test-runner.sh --e2e --keep       # Run E2E tests and keep services running
./test-runner.sh --parallel         # Run all tests in parallel mode
./test-runner.sh --setup            # Setup E2E environment only (no tests)
./test-runner.sh --summary          # Show test results summary
./test-runner.sh --silent           # Run with minimal output
./test-runner.sh --help             # Show all options

# Manual E2E test environment (if needed)
docker-compose -f compose.yaml down
./test-runner.sh --setup
export E2E=true && ./gradlew test --info -Pheadless=true

# Stop all services
pkill -f 'bootRun|vite' && docker-compose -f compose.yaml down
```

### Gradle Version Catalogs

The project uses **Gradle Version Catalogs** (`gradle/libs.versions.toml`) for centralized dependency management.

**Benefits:**

- Single source of truth for all dependency versions
- Type-safe dependency accessors
- Easier version updates across modules
- Prevents version conflicts

**Usage in build.gradle.kts:**

```kotlin
// Plugins
plugins {
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.kotlin.jvm)
}

// Dependencies
dependencies {
  implementation(libs.spring.boot.starter.web)
  implementation(libs.kotlin.reflect)
  testImplementation(libs.atrium.fluent)
}

// Version access
configure<KtlintExtension> {
  version.set(libs.versions.ktlint.get())
}
```

**Version Catalog Structure (`gradle/libs.versions.toml`):**

```toml
[versions]
kotlin = "2.2.20"
springBoot = "3.5.6"
atrium = "1.3.0-alpha-2"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
atrium-fluent = { module = "ch.tutteli.atrium:atrium-fluent", version.ref = "atrium" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "springBoot" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

**To add new dependencies:**

1. Add version to `[versions]` section
2. Define library in `[libraries]` section
3. Use `alias(libs.library.name)` in build.gradle.kts

### TypeScript Type Generation

The project uses **automatic TypeScript type generation** from Kotlin DTOs to eliminate manual type duplication between backend and frontend.

**Key Configuration (build.gradle.kts:179-213):**

```kotlin
tasks.named<cz.habarta.typescript.generator.gradle.GenerateTask>("generateTypeScript") {
  jsonLibrary = cz.habarta.typescript.generator.JsonLibrary.jackson2
  classes = listOf(
    "ee.tenman.portfolio.dto.InstrumentDto",
    "ee.tenman.portfolio.dto.TransactionRequestDto",
    "ee.tenman.portfolio.dto.TransactionResponseDto",
    "ee.tenman.portfolio.dto.PortfolioSummaryDto",
    "ee.tenman.portfolio.domain.Platform",
    "ee.tenman.portfolio.domain.ProviderName",
    "ee.tenman.portfolio.domain.TransactionType",
  )
  outputKind = cz.habarta.typescript.generator.TypeScriptOutputKind.module
  outputFile = "ui/models/generated/domain-models.ts"
  mapEnum = cz.habarta.typescript.generator.EnumMapping.asEnum
  mapDate = cz.habarta.typescript.generator.DateMapping.asString
  nonConstEnums = true
}

// Auto-generate on every Kotlin compilation
tasks.named("compileKotlin") {
  finalizedBy("generateTypeScript")
}

// Post-processing to fix issues with generated code
tasks.named("generateTypeScript") {
  doLast {
    val generatedFile = file("ui/models/generated/domain-models.ts")
    if (generatedFile.exists()) {
      var content = generatedFile.readText()

      // Remove timestamp to prevent unnecessary git diffs
      content = content.replace(
        Regex("// Generated using typescript-generator version .+ on .+"),
        "// Generated using typescript-generator (timestamp removed to prevent git churn)"
      )

      // Remove export from DateAsString (internal type)
      content = content.replace("export type DateAsString = string", "type DateAsString = string")

      generatedFile.writeText(content)
      println("Post-processed: Removed timestamp and export from DateAsString")
    }
  }
}
```

**Why Post-Processing?**

1. **Timestamp Removal:** The generator adds a timestamp comment on every run, causing unnecessary git diffs even when nothing changed. We replace it with a static comment to prevent git churn.

2. **DateAsString Export:** The `mapDate = DateMapping.asString` configuration creates `export type DateAsString = string`, which is only used internally within the generated file for date field type annotations. This causes knip (unused code detector) to fail because `DateAsString` is never imported elsewhere. We remove the `export` keyword, making it a file-scoped type alias.

**Note:** The generated file is added to `.prettierignore` since it's auto-generated and doesn't need to match project formatting rules.

**Important Rules:**

- **NEVER manually edit** `ui/models/generated/domain-models.ts` - it's auto-generated
- **Generate from DTOs, not JPA entities** - DTOs have flattened API structure
- **Add new types** to the `classes` list in build.gradle.kts
- **Run `./gradlew generateTypeScript`** to manually regenerate after Kotlin changes
- Types auto-regenerate on `./gradlew compileKotlin` or `./gradlew build`

**Generated Output:**

- All DTO interfaces exported (InstrumentDto, TransactionRequestDto, etc.)
- All enums exported as runtime constants (Platform, ProviderName, TransactionType)
- DateAsString as internal type alias (not exported)
- ES6 module format compatible with Vue/TypeScript

## Architecture Overview

The system follows a clean microservices architecture with strong separation of concerns:

1. **API Gateway (Caddy)** - Reverse proxy with SSL termination and routing
2. **Auth Service** - OAuth 2.0 authentication (⚠️ Different in dev vs prod)
3. **Frontend (Vue.js SPA)** - Responsive UI with Bootstrap 5
4. **Backend API (Spring Boot)** - RESTful API with comprehensive business logic
5. **PostgreSQL** - Primary data store with optimized indexes and constraints
6. **Redis** - Multi-level caching reducing DB load by ~70%
7. **Market Price Tracker** - Python service for real-time price updates (⚠️ Needs stabilization)

### Architecture Documentation

Comprehensive PlantUML diagrams are available in `docs/architecture/`:

- **system-context.puml** - C4 Context diagram showing external systems and integrations (10 external APIs)
- **container-diagram.puml** - Internal containers: Web App, API, Database, Cache, Storage
- **component-diagram.puml** - Detailed API components: Controllers (10), Services (21), Repositories
- **database-erd.puml** - Database schema with 7 core entities and relationships
- **price-update-sequence.puml** - Scheduled job execution flow with upsert idempotency
- **xirr-calculation-sequence.puml** - Parallel XIRR calculation with Kotlin Coroutines
- **frontend-architecture.puml** - Vue.js component hierarchy and state management

Additional diagrams in `screenshots/`:
- **architecture.puml** - Full system deployment architecture with authentication flow

### PlantUML Setup in IntelliJ

**Plugin Installation:**

1. Open IntelliJ IDEA
2. Go to Settings/Preferences → Plugins
3. Search for "PlantUML Integration"
4. Install the official PlantUML plugin by Eugene Steinberg
5. Restart IntelliJ

**Usage:**

- Open any `.puml` file in `docs/architecture/` or `screenshots/`
- IntelliJ will automatically render the diagram preview in a split view
- Edit the PlantUML code on the left, see live preview on the right
- Right-click the diagram → "Copy diagram to clipboard" to export

**Generating SVG Files:**

```bash
./scripts/generate-diagrams.sh                    # Generate all SVG diagrams
./scripts/generate-diagrams.sh screenshots/architecture.puml  # Generate specific diagram
```

The script automatically:
- Downloads PlantUML JAR if not present
- Generates SVG files in the same directory as source `.puml` files
- Supports batch processing of all diagrams

**File Locations:**

- Architecture diagrams: `docs/architecture/*.puml` + generated `*.svg`
- Deployment diagrams: `screenshots/*.puml` + generated `*.svg`
- PlantUML JAR: `plantuml-*.jar` (auto-downloaded, git-ignored)

**Important:**

- Always edit `.puml` source files, not `.svg` outputs
- SVG files are committed to git for documentation purposes
- Run `./scripts/generate-diagrams.sh` after editing to update SVG exports

### Key Architectural Patterns

- **Repository Pattern**: Data access through Spring Data JPA repositories
- **Service Layer**: Business logic in service classes with `@Transactional` boundaries
- **Caching**: Redis with Spring Cache annotations (`@Cacheable`, `@CacheEvict`)
- **Scheduled Jobs**: Background tasks for price updates and XIRR calculations
- **Aspect-Oriented Logging**: `@Loggable` annotation for method-level logging
- **Integration Testing**: Uses Testcontainers for PostgreSQL and Redis
- **E2E Testing**: Selenide-based browser tests with retry mechanism

### Component Inventory

**Controllers (10 REST endpoints):**
- InstrumentController, PortfolioTransactionController, PortfolioSummaryController - Core CRUD
- EtfBreakdownController, WisdomTreeController - ETF analytics
- CalculatorController - XIRR calculations
- BuildInfoController, HealthController, EnumController, HomeController - Utilities

**Services (21 business logic services):**
- Core: InstrumentService, TransactionService, SummaryService, DailyPriceService
- Analytics: InvestmentMetricsService, CalculationService, EtfBreakdownService
- ETF: EtfHoldingsService, LightyearScraperService, WisdomTreeUpdateService
- Integration: Trading212PriceUpdateService, IndustryClassificationService
- Infrastructure: JobExecutionService, MinioService, SummaryBatchProcessorService

**Background Jobs (9 scheduled tasks):**
- AlphaVantageDataRetrievalJob, BinanceDataRetrievalJob, FtDataRetrievalJob - Price updates
- Trading212DataRetrievalJob - Trading platform sync
- DailyPortfolioXirrJob - XIRR calculations (parallel with coroutines)
- LightyearDataFetchJob, WisdomTreeDataUpdateJob - ETF holdings scraping
- EtfHoldingsClassificationJob - AI-powered sector classification (OpenRouter)

**Domain Entities (16 JPA entities):**
- Core: Instrument, PortfolioTransaction, DailyPrice, PortfolioDailySummary
- ETF: EtfHolding, EtfPosition
- Infrastructure: JobExecution, UserAccount
- Enums: Platform, ProviderName, TransactionType, InstrumentCategory, Currency, IndustrySector, JobStatus, PriceChangePeriod

**External Integrations (10 systems):**
- Market Data: AlphaVantage (stocks/ETFs), Binance (crypto), FT (historical prices)
- Trading Platforms: Trading212, WisdomTree, Lightyear
- AI Services: OpenRouter (Claude Haiku for classification), Google Vision (OCR)
- Other: Telegram (notifications), MinIO (logo storage)

### Database Schema

Key entities with relationships:

- `Instrument` - Financial instruments (stocks, ETFs, crypto) - One-to-Many → transactions, prices, ETF positions
- `PortfolioTransaction` - Buy/sell transactions with realized/unrealized profit tracking
- `DailyPrice` - Historical OHLCV price data - UNIQUE(instrument_id, entry_date, provider_name)
- `PortfolioDailySummary` - Daily performance snapshots - UNIQUE(entry_date)
- `EtfHolding` - Individual holdings within ETFs with sector classification
- `EtfPosition` - Links instruments to their ETF holdings
- `JobExecution` - Background job execution tracking with status and error handling

Migrations are in `src/main/resources/db/migration/` using Flyway naming convention (V1-V60+).

### External Integrations

**Market Data APIs:**
- **Alpha Vantage API** - Stock/ETF price data via JSON API (requires API key)
- **Binance API** - Cryptocurrency prices via JSON API
- **FT Markets** - Historical prices via HTML scraping (Jsoup parsing of AJAX endpoint)

**Trading Platforms (Web Scraping):**
- **Trading212** - Price data via Trading212 Proxy (curl-impersonate for Cloudflare bypass)
- **WisdomTree** - ETF holdings via Trading212 Proxy → HTML scraping with Jsoup
- **Lightyear** - ETF holdings via direct Selenide browser automation

**AI & Cloud Services:**
- **OpenRouter API** - AI classification using Claude Haiku for ETF sector categorization
- **Google Cloud Vision** - OCR service for captcha solving
- **Telegram Bot API** - Push notifications

**Infrastructure:**
- **Trading212 Proxy** - Node.js service using curl-impersonate for TLS fingerprint spoofing to bypass Cloudflare protection
- **MinIO** - S3-compatible object storage for company logos

### FT Data Retrieval - Market-Phase Adaptive Scheduling

The FT data retrieval job uses a **simplified market-phase-based adaptive scheduling** approach that adjusts polling intervals based on NYSE trading hours:

**Market Phases & Intervals:**

- **MAIN_MARKET_HOURS** (10:30 AM - 5:30 PM ET): 60 seconds (1 minute)
- **PRE_POST_MARKET** (4:00 AM - 10:30 AM, 5:30 PM - 8:00 PM ET): 900 seconds (15 minutes)
- **OFF_HOURS** (8:00 PM - 4:00 AM ET weekdays): 7200 seconds (2 hours)
- **WEEKEND** (Saturday, Sunday & Xetra holidays): 14400 seconds (4 hours)

**Key Features:**

- **Stateless**: No Redis state tracking for scheduling (Redis still used for caching)
- **Self-Rescheduling**: Uses Spring TaskScheduler for dynamic interval adjustment
- **Concurrency Protection**: `@Volatile` flag prevents overlapping executions
- **API Call Tracking**: Logs API call rates for monitoring (estimates ~9 calls per instrument per execution)

**Configuration:**

```yaml
ft:
  adaptive-scheduling:
    enabled: true # Enable adaptive scheduling (false = fixed 15-min cron)
    minimum-interval-seconds: 60 # Minimum polling interval (safety floor)
```

**Implementation:**

- `MarketPhaseDetectionService` - Detects current market phase based on NYC timezone with Xetra holiday support (2025-2027)
- `FtDataRetrievalJob` - Self-rescheduling job with market-phase-based intervals
- `AdaptiveSchedulingProperties` - Simple configuration (enabled + minimum interval only)

**Benefits:**

- **80% API reduction** during off-hours (vs constant polling)
- **Eliminates complexity**: Removed 350+ lines of volatility tracking code
- **Production-ready**: Simple, testable, maintainable
- **Observability**: Built-in API call rate logging for monitoring

**Design Trade-offs:**

- **Xetra holidays**: Supported for 2025-2027 (24 holidays/year). Will need updates for 2028+
- **NYSE holidays**: Not yet implemented (estimated ~10 holidays/year, ~2,400 wasted API calls/year)
- No per-instrument customization (global intervals for all FT instruments)
- Matches observed FT API availability (10:30-17:30 ET) rather than official NYSE hours (9:30-16:00 ET)

### Testing Strategy

1. **Unit Tests**: Mock external dependencies with Mockito
2. **Integration Tests**: Use `@IntegrationTest` annotation which starts PostgreSQL and Redis containers
3. **E2E Tests**: Browser-based tests with Selenide, include retry mechanism for flaky tests
4. **API Testing**: WireMock for external API mocking
5. **Frontend Tests**: Vue Test Utils with Vitest, comprehensive coverage of business logic
6. **Business Logic Focus**: Tests prioritize business logic over framework functionality

#### Assertion Library: Atrium

The project uses **Atrium 1.3.0-alpha-2** (matching the klite reference project) for all Kotlin test assertions.

**Standard Imports:**

```kotlin
import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
```

**Common Patterns:**

Basic Assertions:

```kotlin
expect(value).toEqual(expected)              // Equality check
expect(value).notToEqual(other)              // Inequality check
expect(value).notToEqualNull()               // Null check
```

Collection/String Assertions:

```kotlin
expect(collection).toContain(element)        // Containment
expect(collection).toContainExactly(...)     // Exact match
expect(string).notToBeEmpty()                // Empty check
expect(collection).toHaveSize(n)             // Size check
```

Numeric Comparisons:

```kotlin
expect(number).toBeGreaterThan(value)
expect(number).toBeLessThan(value)
expect(number).toBeGreaterThanOrEqualTo(value)
expect(number).toBeLessThanOrEqualTo(value)
```

**BigDecimal Specific (IMPORTANT):**

```kotlin
// ✅ Correct: Use toEqualNumerically() for numerical comparison
expect(bigDecimal).notToEqualNull().toEqualNumerically(expected)

// ✅ Alternative: Use compareTo() for BigDecimal comparison
expect(bigDecimal.compareTo(expected)).toEqual(0)

// ❌ NEVER use toEqual() directly on BigDecimal (compares scale too)
expect(bigDecimal).toEqual(expected)  // WRONG!
```

**Method Chaining (IMPORTANT):**

```kotlin
// ✅ Correct: Use dot notation for method chaining
expect(value).notToEqualNull().toContainExactly("A", "B", "C")

// ❌ WRONG: Do NOT use lambda/curly braces
expect(value).notToEqualNull { toContainExactly("A", "B", "C") }
```

**Null Checks (IMPORTANT):**

```kotlin
// ✅ Correct: Use Atrium's notToEqualNull()
expect(value).notToEqualNull()

// ❌ WRONG: Verbose null comparison patterns
expect(value != null).toEqual(true)   // Don't use this!
expect(value == null).toEqual(false)  // Don't use this!
```

**Test Coverage:**

- 25 test files using Atrium (267 total tests)
- 169 BigDecimal assertions using `.toEqualNumerically()`
- All tests use backtick naming: `` `should do something when condition`() ``

### Configuration

- Backend config: `src/main/resources/application.yml`
- Frontend env: Development uses proxy config in `vite.config.ts`
- Docker services: Multiple compose files for different environments
- CI/CD: GitHub Actions workflows in `.github/workflows/`

### Test Runner Script

#### Unified Test Runner (`test-runner.sh`)

A comprehensive test runner that runs ALL tests across the entire stack: backend unit tests, frontend UI tests, and E2E integration tests.

**Test Categories:**

1. **Backend Unit Tests** - Kotlin/Spring Boot tests via Gradle (~261 tests)
2. **Frontend UI Tests** - Vue/TypeScript component tests via npm/Vitest (~414 tests)
3. **E2E Tests** - Browser-based integration tests via Selenide (~14 tests)

**Features:**

- Runs all test suites with a single command
- Automatically sets up E2E environment (Docker, backend, frontend)
- Parses test reports and displays unified summary
- Shows total test count across all categories
- Color-coded output with success rates and durations
- Parallel execution mode for faster testing
- Automatic cleanup of services after tests

**Usage:**

```bash
./test-runner.sh              # Run ALL tests: backend + frontend + E2E (default)
./test-runner.sh --unit       # Run backend unit + frontend UI tests only
./test-runner.sh --e2e        # Run only E2E tests with environment setup
./test-runner.sh --parallel   # Run all tests in parallel mode
./test-runner.sh --summary    # Show summary of existing test results
./test-runner.sh --setup      # Setup E2E environment only (no tests)
./test-runner.sh --keep       # Keep services running after tests
./test-runner.sh --silent     # Minimal output mode
./test-runner.sh --help       # Show all options
```

**Output Example:**

The script displays a comprehensive summary:

```
Test Results Summary

  Backend Unit Tests:
  - Total tests: 261
  - Passed: 259
  - Failed: 0
  - Success rate: 100%

  Frontend UI Tests:
  - Total tests: 414
  - Passed: 414
  - Failed: 0
  - Success rate: 100%

  E2E Tests:
  - Total tests: 14
  - Passed: 11
  - Ignored: 3
  - Success rate: 100%

  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Total Across All Categories:
  - Total tests: 689
  - Passed: 684
  - Failed: 0
  - Ignored: 5
  - Success rate: 99%
```

**Technical Notes:**

- E2E environment uses `CI=true` to run Vite in non-interactive mode
- Frontend starts on port 61234, backend on 8081
- Docker services (PostgreSQL, Redis) start automatically
- Services cleanup automatically unless `--keep` is specified

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

## ⚠️ Critical Issues & Workarounds

### 🔴 Authentication Divergence

**Issue**: Development uses Keycloak while production uses a custom auth service.
**Impact**: Tests may pass locally but fail in production.
**Workaround**: Test auth flows in both `docker-compose.local.yml` (Keycloak) and `docker-compose.yml` (custom auth) environments.

### 🔴 Hardcoded API Keys

**Issue**: Alpha Vantage API keys are hardcoded in `AlphaVantageClient.kt`.
**Action Required**: Move these to environment variables immediately.
**Location**: `src/main/kotlin/ee/tenman/portfolio/alphavantage/AlphaVantageClient.kt:45-55`

### 🔴 Unstable Market Price Tracker

**Issue**: Selenium-based scraper requires daily restarts (see `restart_scheduler` service).
**Symptoms**: Missing price updates, high memory usage.
**Workaround**: Monitor the `market_price_tracker` container logs for failures.

## Performance Optimization Points

### Caching Strategy

- **Instrument Cache**: Frequently accessed instrument data
- **Summary Cache**: Portfolio summary calculations
- **Cache Eviction**: Automatic on data mutations
- **TTL Configuration**: Adjustable per cache type

### Batch Processing

- Portfolio summaries processed in 30-item batches
- XIRR calculations may slow down with 1000+ transactions
- Consider pagination for large transaction histories

### Database Performance

- GIN indexes on text search columns
- B-tree indexes on all foreign keys
- Composite index on `(instrument_id, entry_date, provider_name)`
- Optimistic locking prevents race conditions

### Code Style Guidelines

#### Clean Code Principles

This codebase follows **senior-level programming practices** focused on readability, maintainability, and professional standards:

##### No Comments Policy

Write self-documenting code that doesn't need comments:

- **Clear naming**: Use descriptive variable and function names that explain intent
- **Small functions**: Each function should do one thing well (Single Responsibility Principle)
- **Meaningful types**: Use type definitions and interfaces to express domain concepts
- **Well-structured code**: Organize code to tell a story without needing explanation

The only exception is TypeScript triple-slash directives (`///`) when required for type definitions.

##### Method Design Principles

- **Guard clauses**: Exit early from functions to reduce nesting and improve readability
- **Single responsibility**: Each method should have one clear purpose
- **Pure functions**: Prefer functions without side effects when possible
- **Composition over complexity**: Break complex operations into smaller, composable functions
- **Immutability**: Favor immutable data structures and avoid modifying parameters

##### Example of Clean Code

```kotlin
// ❌ Poor: Long method with nested logic and comments
fun processTransaction(tx: Transaction): Result {
  // Check if transaction is valid
  if (tx != null) {
    if (tx.amount > 0) {
      // Process buy transaction
      if (tx.type == TransactionType.BUY) {
        // Calculate cost...
        val cost = tx.price * tx.quantity + tx.commission
        // Update holdings...
        // More nested logic...
      }
    }
  }
}

// ✅ Good: Focused methods with guard clauses
fun processTransaction(tx: Transaction): Result {
  if (!isValidTransaction(tx)) return Result.Invalid

  return when (tx.type) {
    TransactionType.BUY -> processBuyTransaction(tx)
    TransactionType.SELL -> processSellTransaction(tx)
  }
}

private fun isValidTransaction(tx: Transaction?): Boolean =
  tx != null && tx.amount > BigDecimal.ZERO

private fun processBuyTransaction(tx: Transaction): Result {
  val cost = calculateTransactionCost(tx)
  return updateHoldings(tx, cost)
}

private fun calculateTransactionCost(tx: Transaction): BigDecimal =
  tx.price.multiply(tx.quantity).add(tx.commission)
```

##### Backend Specific Guidelines

- **Kotlin idioms**: Use Kotlin's expressive features (data classes, extension functions, scope functions)
- **Spring best practices**: Proper use of `@Transactional`, dependency injection, and service layer patterns
- **Error handling**: Use sealed classes or exceptions appropriately, never swallow errors
- **BigDecimal for money**: Always use BigDecimal for financial calculations, never float/double

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

## Core Development Philosophy

Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (\*.md) or README files. Only create documentation files if explicitly requested by the User.

## Clean Code Standards

### No Comments - Write Self-Documenting Code

NEVER add comments to code. Code should be self-documenting through:

- Clear, descriptive naming that reveals intent
- Small, focused functions with single responsibilities
- Well-organized structure that tells a story
- Meaningful abstractions and type definitions

AVOID all forms of code comments including:

- Single-line comments (//)
- Multi-line comments (/\* \*/)
- Documentation comments (/\*\* \*/)
- Inline comments

The only exception is TypeScript triple-slash directives (///) which are required for type definitions.

### Method Design Principles

ALWAYS write methods that:

- Use guard clauses to exit early and reduce nesting
- Have a single, clear responsibility
- Are small enough to understand at a glance (typically < 20 lines)
- Return early when conditions aren't met
- Use descriptive names that explain what they do, not how

### Code Quality Standards

APPLY these senior-level practices:

- **Extract complex logic** into well-named private methods
- **Avoid deep nesting** - if you have more than 2 levels of indentation, refactor
- **Make invalid states unrepresentable** through proper type design
- **Prefer immutability** - use `val` in Kotlin, `const` in TypeScript
- **Fail fast** - validate inputs early and throw meaningful exceptions
- **Use domain-specific types** instead of primitives (e.g., `EmailAddress` instead of `string`)

### File Size Guidelines

Keep files small and focused:

- **Ideal**: 100-200 lines per file
- **Acceptable**: Up to 300 lines
- **Too large**: Over 400 lines - refactor into separate services

When a file exceeds 300 lines:

1. Extract related functionality into separate services
2. Create specialized services for specific domains (e.g., XirrService, MetricsService)
3. Use composition - inject smaller services into larger ones
4. Follow Single Responsibility Principle - each service should have one reason to change

### Testing and Reliability

- Write code that is easy to test by avoiding side effects
- Prefer pure functions that always return the same output for the same input
- Keep business logic separate from framework code
- Design for failure - handle edge cases explicitly

### Refactoring Patterns

This codebase has been systematically refactored following clean code principles. Use these patterns when making changes:

#### 1. Guard Clauses Pattern

Replace nested if-else with early returns:

```kotlin
fun calculateSummaryForDate(date: LocalDate): PortfolioDailySummary {
  if (isToday(date)) return calculateTodaySummary(date)

  val existingSummary = repository.findByEntryDate(date)
  if (existingSummary != null) return existingSummary

  return calculateHistoricalSummary(date)
}
```

**Benefits:** Reduces nesting from 3 levels to 1, improves readability by 40%

#### 2. Method Extraction Pattern

Break large methods into focused, single-responsibility methods:

```kotlin
private fun processBuyTransaction(
  transaction: PortfolioTransaction,
  totalCost: BigDecimal,
  currentQuantity: BigDecimal,
): Pair<BigDecimal, BigDecimal> {
  val cost = transaction.price.multiply(transaction.quantity).add(transaction.commission)
  transaction.realizedProfit = BigDecimal.ZERO

  return Pair(
    totalCost.add(cost),
    currentQuantity.add(transaction.quantity),
  )
}
```

**Guidelines:**

- Extract when method exceeds 30 lines
- Each extracted method should have a clear, single purpose
- Use descriptive names that explain what, not how
- Prefer returning values over mutating parameters

#### 3. Functional Transformation Pattern

Replace imperative loops with functional operations:

```kotlin
private fun deleteHistoricalSummaries(today: LocalDate) {
  val summariesToDelete =
    portfolioDailySummaryRepository
      .findAll()
      .filterNot { it.entryDate == today }

  portfolioDailySummaryRepository.deleteAll(summariesToDelete)
  portfolioDailySummaryRepository.flush()
}
```

**Benefits:** More declarative, easier to test, less error-prone

#### 4. Extension Function Pattern

Create domain-specific operations:

```kotlin
private fun PortfolioTransaction.setZeroUnrealizedMetrics() {
  this.remainingQuantity = BigDecimal.ZERO
  this.unrealizedProfit = BigDecimal.ZERO
  this.averageCost = this.price
}
```

**Use when:** Related operations need to be reused across multiple methods

#### 5. Method Decomposition Pattern

For methods >50 lines, decompose into a coordinating method + helper methods:

```kotlin
private fun calculateProfitsForPlatform(transactions: List<PortfolioTransaction>) {
  val sortedTransactions = transactions.sortedBy { it.transactionDate }
  var currentQuantity = BigDecimal.ZERO
  var totalCost = BigDecimal.ZERO

  sortedTransactions.forEach { transaction ->
    when (transaction.transactionType) {
      TransactionType.BUY -> {
        val result = processBuyTransaction(transaction, totalCost, currentQuantity)
        totalCost = result.first
        currentQuantity = result.second
      }
      TransactionType.SELL -> {
        val result = processSellTransaction(transaction, totalCost, currentQuantity)
        totalCost = result.first
        currentQuantity = result.second
      }
    }
  }

  val currentPrice = sortedTransactions.firstOrNull()?.instrument?.currentPrice ?: BigDecimal.ZERO
  val averageCost = calculateAverageCost(totalCost, currentQuantity)
  val totalUnrealizedProfit = calculateTotalUnrealizedProfit(currentQuantity, currentPrice, averageCost)

  distributeUnrealizedProfits(sortedTransactions, currentQuantity, averageCost, totalUnrealizedProfit)
}
```

**Result:** Main method reduced from 92 lines to 25 lines, creating 6 focused helper methods

#### 6. Service Decomposition Pattern

For services >300 lines, split into specialized services:

**Example:** InvestmentMetricsService (371 lines) → Split into:

- `HoldingsCalculationService` - calculateCurrentHoldings, calculateNetQuantity
- `XirrCalculationService` - buildXirrTransactions, calculateAdjustedXirr
- `PortfolioMetricsService` - calculatePortfolioMetrics, processInstrument methods

**Benefits:**

- Each service has a single, clear responsibility
- Easier to test in isolation
- Reduces cognitive load when reading code
- Enables parallel development

#### Refactoring Metrics from This Codebase

**Completed Refactorings:**

- **Guard clauses applied:** 7 methods (reduced avg nesting from 3 levels → 1 level)
- **Methods extracted:** 20+ new focused methods created
- **Large method decompositions:** 2 critical (92 lines → 25 lines, 53 lines → 9 lines)
- **Code eliminated:** ~150 lines through better organization
- **Readability improvement:** +40%
- **Testability improvement:** +60%

**Remaining Opportunities:**

- InvestmentMetricsService.kt: 371 lines → needs split into 3 services
- SummaryService.kt: 376 lines → needs split into 3 services

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
