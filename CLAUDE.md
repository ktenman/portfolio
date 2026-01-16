# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Portfolio Management System** - a production-ready, full-stack application for tracking investment portfolios with automated price updates and sophisticated performance calculations.

**Tech Stack:**

- Backend: Kotlin 2.2, Spring Boot 4.0, Java 21
- Frontend: Vue.js 3.5, TypeScript 5.9, Vite 7.3, Bootstrap 5.3
- Database: PostgreSQL 17 with Flyway migrations (V1-V126+)
- Cache: Redis 8 (multi-level caching strategy)
- Testing: Atrium 1.3 (Kotlin assertions), JUnit 5, MockK, Selenide, Vitest
- Build: Gradle 8.8 with Version Catalogs (libs.versions.toml)
- Authentication: Custom Spring Boot auth service (https://github.com/ktenman/auth)
- Infrastructure: Docker, Kubernetes, Caddy reverse proxy
- Additional Services: Google Cloud Vision API

## Git Branching Strategy

When working on features or bug fixes:

1. **Always create a branch from the related GitHub issue** when possible
   - Use format: `feature/<issue-number>-<short-description>` (e.g., `feature/1035-circuit-breaker-openrouter`)
   - For bug fixes: `fix/<issue-number>-<short-description>`
2. **Never commit directly to main** for non-trivial changes
3. **Create PRs that reference the issue** with "Closes #XXX" or "Fixes #XXX"
4. **If CI fails on main**, reset main to the last good commit and move failing changes to a feature branch
5. **Squash related commits** when moving work to a feature branch to keep history clean and then squash and then close pr and create new pr

## Essential Commands

### Quick Start

Run backend and frontend together with a single command:

```bash
npm run dev                 # Starts both backend and frontend with colored output
```

This command automatically:

- Cleans up any existing Docker containers and processes on ports 8081/61234
- Starts Spring Boot backend on http://localhost:8081
- Starts Vite frontend dev server on http://localhost:61234
- Shows color-coded, prefixed output (blue for backend, green for frontend)

You can also run services individually:

```bash
npm run dev:ui              # Start frontend only (Vite)
npm run dev:backend         # Start backend only (Gradle bootRun)
```

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

# E2E tests (use npm script for complete setup)
npm run test:e2e              # Recommended: Full E2E setup + tests
# OR manual E2E (requires environment setup first):
npm run test:setup && E2E=true ./gradlew test --info -Pheadless=true
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
npm run dev                 # Start both backend + frontend (recommended)
npm run dev:ui              # Start frontend only (port 61234)
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
docker compose -f compose.yaml up -d                    # Start PostgreSQL & Redis only
docker compose -f docker-compose.local.yml build        # Build all services
docker compose -f docker-compose.local.yml up -d        # Run full stack

# Test Runner (RECOMMENDED - runs all tests via npm)
npm run test:all                    # Run ALL tests: backend + frontend + E2E
npm run test:unit                   # Run backend unit + frontend UI + proxy tests
npm run test:e2e                    # Run E2E tests (starts all services automatically)
npm run test:proxy                  # Run cloudflare-bypass-proxy tests only
npm run test:setup                  # Setup E2E environment (Docker + backend + frontend)
npm run test:wait                   # Wait for backend and frontend to be ready
npm run test:cleanup                # Stop all services and cleanup

# Manual E2E test environment (if needed)
docker compose -f compose.yaml down
npm run test:setup
export E2E=true && ./gradlew test --info -Pheadless=true

# Stop all services
npm run test:cleanup
```

### Gradle Version Catalogs

Dependencies are managed via Gradle Version Catalogs in `gradle/libs.versions.toml`. Use `alias(libs.library.name)` in build.gradle.kts.

### TypeScript Type Generation

Auto-generates TypeScript types from Kotlin DTOs to `ui/models/generated/domain-models.ts`.

**Rules:**

- **NEVER manually edit** generated file - it's auto-generated
- **Add new types** to the `classes` list in `build.gradle.kts:179-213`
- Types auto-regenerate on `./gradlew compileKotlin` or `./gradlew build`
- Post-processing removes timestamps and unexports DateAsString (for knip compatibility)

## Architecture Overview

The system follows a clean microservices architecture with strong separation of concerns:

1. **API Gateway (Caddy)** - Reverse proxy with SSL termination and routing
2. **Auth Service** - OAuth 2.0 authentication (⚠️ Different in dev vs prod)
3. **Frontend (Vue.js SPA)** - Responsive UI with Bootstrap 5
4. **Backend API (Spring Boot)** - RESTful API with comprehensive business logic
5. **PostgreSQL** - Primary data store with optimized indexes and constraints
6. **Redis** - Multi-level caching reducing DB load by ~70%

### Architecture Documentation

Comprehensive PlantUML diagrams are available in `docs/architecture/`:

- **system-context.puml** - C4 Context diagram showing external systems and integrations (12 external APIs)
- **container-diagram.puml** - Internal containers: Web App, API, Database, Cache, Storage
- **component-diagram.puml** - Detailed API components: Controllers (11), Services (48+), Repositories
- **database-erd.puml** - Database schema with 7 core entities and relationships
- **price-update-sequence.puml** - Scheduled job execution flow with upsert idempotency
- **xirr-calculation-sequence.puml** - Parallel XIRR calculation with Kotlin Coroutines
- **frontend-architecture.puml** - Vue.js component hierarchy and state management

Additional diagrams in `screenshots/`:

- **architecture.puml** - Full system deployment architecture with authentication flow

PlantUML diagrams can be generated with `./scripts/generate-diagrams.sh`. Edit `.puml` source files, not `.svg` outputs.

### Key Architectural Patterns

- **Repository Pattern**: Data access through Spring Data JPA repositories
- **Service Layer**: Business logic in service classes with `@Transactional` boundaries
- **Caching**: Redis with Spring Cache annotations (`@Cacheable`, `@CacheEvict`)
- **Scheduled Jobs**: Background tasks for price updates and XIRR calculations
- **Aspect-Oriented Logging**: `@Loggable` annotation for method-level logging
- **Integration Testing**: Uses Testcontainers for PostgreSQL and Redis
- **E2E Testing**: Selenide-based browser tests with retry mechanism

### Component Inventory

**Controllers (11 REST endpoints):**

- InstrumentController, PortfolioTransactionController, PortfolioSummaryController - Core CRUD
- EtfBreakdownController - ETF analytics
- CalculatorController - XIRR calculations
- LogoController - Logo management for ETF holdings
- VehicleInfoController - Vehicle tracking and valuation
- BuildInfoController, HealthController, EnumController, HomeController - Utilities

**Services (48+ business logic services):**

_Calculation:_ CalculationService, HoldingsCalculationService, InvestmentMetricsService, ProfitCalculationEngine, XirrCalculationService, InvestmentMath

_ETF:_ EtfBreakdownDataLoaderService, EtfBreakdownService, EtfHoldingPersistenceService, EtfHoldingService, HoldingAggregationService, SyntheticEtfCalculationService

_Infrastructure:_ CacheInvalidationService, ImageDownloadService, ImageProcessingService, JobExecutionService, JobTransactionService, MinioService

_Logo:_ BatchLogoValidationService, ImageSearchLogoService, LogoCacheService, LogoCandidateCacheService, LogoFallbackService, LogoReplacementService, LogoValidationService, NvstlyLogoService, OpenRouterLogoSelectionService

_Pricing:_ DailyPriceService, LightyearPriceUpdateService, PriceRefreshService, PriceUpdateProcessor, Trading212PriceUpdateService

_Summary:_ DailySummaryCalculator, SummaryBatchProcessorService, SummaryCacheService, SummaryDeletionService, SummaryPersistenceService, SummaryService

_Transaction:_ TransactionCacheService, TransactionCalculationService, TransactionProfitService, TransactionQueryService, TransactionService

_Instrument:_ InstrumentService, InstrumentSnapshotService

_Common:_ EasterHolidayService, EnumService, OnceInstrumentDataRetrievalService

_Integration:_ CountryClassificationService, IndustryClassificationService

_Vehicle:_ LicensePlateDetectionService, VehicleInfoService

**Background Jobs (13 scheduled tasks):**

_Price Updates:_ BinanceDataRetrievalJob, FtDataRetrievalJob, Trading212DataRetrievalJob, LightyearPriceRetrievalJob, LightyearHistoricalDataRetrievalJob, InstrumentPriceGapFillingJob

_XIRR & Analytics:_ DailyPortfolioXirrJob, InstrumentXirrJob

_ETF Holdings:_ LightyearDataFetchJob, EtfHoldingsClassificationJob, EtfCountryClassificationJob, EtfLogoCollectionJob, TerUpdateJob

**Domain Entities (7 JPA entities + 15 enums):**

_JPA Entities:_ Instrument, PortfolioTransaction, DailyPrice, PortfolioDailySummary, EtfHolding, EtfPosition, JobExecution

_Enums:_ Platform, ProviderName, TransactionType, InstrumentCategory, Currency, IndustrySector, JobStatus, PriceChangePeriod, SectorSource, VisionModel, DetectionProvider, AiModel, LogoSource

**External Integrations (12 systems):**

- Market Data: FT (stocks/ETFs), Binance (crypto)
- Trading Platforms: Trading212, Lightyear
- AI Services: OpenRouter (Claude Haiku for classification), Google Vision (OCR)
- Vehicle: Auto24 (Estonian car marketplace valuation), Veego (tax reporting)
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

Migrations are in `src/main/resources/db/migration/` using Flyway naming convention (V1-V126+).

### External Integrations

**Market Data APIs:**

- **Binance API** - Cryptocurrency prices via JSON API
- **FT Markets** - Stock/ETF prices via HTML scraping (Jsoup parsing of AJAX endpoint)

**Trading Platforms (Web Scraping):**

- **Trading212** - Price data via Cloudflare Bypass Proxy (curl-impersonate for Cloudflare bypass)
- **Lightyear** - Price data and ETF holdings via Cloudflare Bypass Proxy (curl-impersonate)

**Vehicle Tracking:**

- **Auto24** - Estonian car marketplace for vehicle valuation
- **Veego** - Tax reporting service for vehicle calculations

**AI & Cloud Services:**

- **OpenRouter API** - AI classification using Claude Haiku for ETF sector categorization
- **Google Cloud Vision** - OCR service for captcha solving
- **Telegram Bot API** - Push notifications

**Infrastructure:**

- **Cloudflare Bypass Proxy** - Node.js/TypeScript service using curl-impersonate for TLS fingerprint spoofing to bypass Cloudflare protection
- **MinIO** - S3-compatible object storage for company logos

### FT Data Retrieval - Adaptive Scheduling

FT job uses market-phase-based adaptive scheduling (60s during market hours, 15min pre/post, 2hr off-hours, 4hr weekends). Config in `ft.adaptive-scheduling`. Key classes: `MarketPhaseDetectionService`, `FtDataRetrievalJob`.

### Testing Strategy

1. **Unit Tests**: Mock external dependencies with MockK (NOT Mockito)
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
- Deployment: Automated via GitHub Actions with health check verification

### Test Runner Scripts

The project uses npm scripts for running all tests across the stack: backend unit tests, frontend UI tests, cloudflare-bypass-proxy tests, and E2E integration tests.

**Test Categories:**

1. **Backend Unit Tests** - Kotlin/Spring Boot tests via Gradle (~261 tests)
2. **Frontend UI Tests** - Vue/TypeScript component tests via npm/Vitest (~414 tests)
3. **Cloudflare Bypass Proxy Tests** - Node.js/TypeScript tests via Jest
4. **E2E Tests** - Browser-based integration tests via Selenide (~14 tests)

**Usage:**

```bash
npm run test:all              # Run ALL tests: backend + frontend + E2E
npm run test:unit             # Run backend unit + frontend UI + proxy tests
npm run test:e2e              # Run E2E tests (starts all services automatically)
npm run test:proxy            # Run cloudflare-bypass-proxy tests only
npm run test:setup            # Setup E2E environment (Docker + backend + frontend)
npm run test:wait             # Wait for backend and frontend to be ready
npm run test:cleanup          # Stop all services and cleanup
npm run docker:up             # Start Docker services (PostgreSQL & Redis)
npm run docker:down           # Stop Docker services
```

**Technical Notes:**

- E2E environment uses Docker Compose V2 (`docker compose`)
- Frontend starts on port 61234, backend on 8081
- `npm run test:setup` starts Docker services, backend, and frontend with health check waiting
- `npm run test:e2e` preserves test exit code after cleanup
- Services cleanup automatically after E2E tests
- Backend logs: `/tmp/portfolio-backend.log`, Frontend logs: `/tmp/portfolio-frontend.log`

### Development Tips

- Use `@IntegrationTest` annotation for tests requiring database/Redis
- Frontend API calls go through `/api` proxy in development
- Redis cache keys are defined in `ui/constants/cache-keys.ts`
- Scheduled jobs can be disabled with `scheduling.enabled=false`
- E2E tests generate screenshots on failure (check build artifacts)
- Use `npm run test:e2e` for reliable E2E testing
- Frontend tests focus on business logic with comprehensive coverage
- Test files excluded from coverage: `.eslintrc.cjs` and `app.vue`
- **ALWAYS run `npm run lint-format` after making changes to UI code** - This ensures type safety, linting, and code formatting
- **ALWAYS run `npm test` after making changes to UI code** - This ensures all tests pass and functionality is not broken

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

Replace nested if-else with early returns. Check for the negative/null case first and return early:

```kotlin
fun calculateSummaryForDate(date: LocalDate): PortfolioDailySummary {
  if (isToday(date)) return calculateTodaySummary(date)

  val existingSummary = repository.findByEntryDate(date)
  if (existingSummary != null) return existingSummary

  return calculateHistoricalSummary(date)
}
```

**Null Check Pattern:** Always check for null first and return early, then proceed with the happy path:

```kotlin
private fun parseResponse(response: Response): Result? {
  val content = response.content ?: return null
  val sector = IndustrySector.fromDisplayName(content)
  if (sector == null) {
    log.warn("Unknown sector: {}", content)
    return null
  }
  log.info("Classified as {}", sector.displayName)
  return Result(sector = sector)
}
```

**Key principle:** Check for the negative case (`== null`) and return early, keeping the happy path at the end without nesting.

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

Additional patterns: Functional transformations, extension functions, method decomposition, and service decomposition (split services >300 lines).

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

## CI/CD and Code Review Rules

- All CI workflows must pass before code changes may be reviewed
- The existing code structure must not be changed without a strong reason
- Every bug must be reproduced by a unit test before being fixed
- Every new feature must be covered by a unit test before it is implemented
- Minor inconsistencies and typos in the existing code may be fixed

## Git Commit Conventions

When creating commits:

- Use the global git user configured in the system (`git config --global user.name` and `git config --global user.email`)
- **NEVER** add "Generated with Claude Code" or similar attribution lines to commit messages
- **NEVER** add "Co-Authored-By: Claude" or any AI co-author attribution
- Start with uppercase imperative verb (e.g., "Add", "Fix", "Update", "Remove")
- **NO PREFIXES** - never use `feat:`, `fix:`, `chore:`, `docs:`, etc.
- Subject line max 50 characters
- Good: `Add user authentication`
- Bad: `feat: add user authentication`

## Pull Request Conventions

When creating pull requests:

- **NEVER** add "Generated with Claude Code" or similar attribution lines to PR descriptions
- **NEVER** add any AI attribution or emoji indicators
- Use clear, descriptive titles that summarize the change
- Include a Summary section with bullet points
- Include a Test plan section with checkboxes
- Reference related issues with "Closes #XXX" or "Fixes #XXX"

## Kotlin/Backend Design Principles

### Method and Class Design

- Method bodies may not contain blank lines
- Method and function bodies may not contain comments
- Variable names should be clear single-word nouns when possible
- Method names should be clear single-word verbs when possible (exceptions for Kotlin idioms like `findBySymbol`)
- Error and log messages should not end with a period
- Error and log messages must always be a single sentence
- Favor "fail fast" paradigm over "fail safe": throw exceptions early
- Constructors should only contain assignment statements
- Favor immutable objects (`val`) over mutable ones (`var`)
- Use Kotlin data classes for DTOs and value objects
- Use `runCatching` for error handling instead of try-catch blocks
- Use guard clauses for early returns instead of nested conditionals
- **Data classes must be in separate files** - Never nest data classes inside service classes. Each data class should have its own file for better organization and testability. This is enforced by ArchUnit tests.

### Exception Handling

- Exception messages must include as much context as possible
- Never swallow exceptions silently
- Use domain-specific exceptions when appropriate
- Prefer `runCatching { }.getOrElse { }` over try-catch

### Kotlin-Specific Guidelines

- Use extension functions for domain operations
- Prefer `when` expressions over if-else chains
- Use `?.let { }` and `?: return` for null handling
- Use `also`, `apply`, `let`, `run`, `with` appropriately
- Prefer functional transformations (`map`, `filter`, `fold`) over imperative loops
- Use `generateSequence` instead of while loops with mutable state

### Spring Framework Guidelines

#### @Cacheable Self-Invocation Problem

**CRITICAL**: Spring's `@Cacheable`, `@Transactional`, and other proxy-based annotations do NOT work with self-invocation (calling a method within the same class).

```kotlin
// ❌ WRONG: Cache is BYPASSED - self-invocation
@Service
class MyService {
  @Cacheable("cache")
  fun getCachedData(): Data = repository.findAll()

  fun processData(): Result {
    val data = getCachedData()  // Cache annotation IGNORED!
    return transform(data)
  }
}

// ✅ CORRECT: Use separate service for cached operations
@Service
class MyCacheService(private val repository: MyRepository) {
  @Cacheable("cache")
  fun getCachedData(): Data = repository.findAll()
}

@Service
class MyService(private val cacheService: MyCacheService) {
  fun processData(): Result {
    val data = cacheService.getCachedData()  // Cache works via proxy
    return transform(data)
  }
}

// ✅ ALTERNATIVE: Self-injection with @Lazy
@Service
class MyService(
  @Lazy private val self: MyService,
  private val repository: MyRepository,
) {
  @Cacheable("cache")
  fun getCachedData(): Data = repository.findAll()

  fun processData(): Result {
    val data = self.getCachedData()  // Cache works via proxy
    return transform(data)
  }
}
```

**Why this happens**: Spring AOP uses proxies. When you call a method internally, you bypass the proxy and the annotation is ignored.

#### Network I/O Outside Transactions

**CRITICAL**: Keep network I/O (HTTP calls, file downloads, external API calls) outside `@Transactional` boundaries to avoid holding database connections during slow operations.

```kotlin
// ❌ WRONG: Network I/O inside transaction holds DB connection
@Service
class HoldingService(private val repository: HoldingRepository) {
  @Transactional
  fun saveHoldings(holdings: List<HoldingData>) {
    holdings.forEach { data ->
      val holding = repository.save(Holding(data.name))
      downloadLogo(data.logoUrl)  // Network I/O blocks transaction!
    }
  }
}

// ✅ CORRECT: Separate persistence from network I/O
@Service
class HoldingService(
  private val persistenceService: HoldingPersistenceService,
  private val imageService: ImageDownloadService,
) {
  fun saveHoldings(holdings: List<HoldingData>) {
    val saved = persistenceService.saveAll(holdings)  // Transaction commits here
    saved.forEach { holding ->
      downloadLogo(holding, holding.logoUrl)  // Network I/O outside transaction
    }
  }
}

@Service
class HoldingPersistenceService(private val repository: HoldingRepository) {
  @Transactional
  fun saveAll(holdings: List<HoldingData>): List<Holding> =
    holdings.map { repository.save(Holding(it.name)) }
}
```

**Why this matters**: Database connections are limited resources. Holding them during network calls can exhaust the connection pool and cause timeouts.

### Clean Architecture Principles

**MANDATORY FOR ALL NEW FEATURES**: Clean architecture rules MUST be strictly followed. Non-compliance will result in code review rejection.

#### Enforcement Rules

1. **Controllers MUST be thin** - Maximum 1-2 lines per method, delegation only
2. **Business logic MUST live in services** - Never in controllers or repositories
3. **Services MUST have single responsibility** - One service = one domain concern
4. **Data classes MUST be in separate files** - Never nested inside services
5. **Network I/O MUST be outside transactions** - Separate persistence from external calls
6. **Caching MUST use separate services** - Avoid self-invocation problems
7. **Dependencies MUST flow inward** - Controllers → Services → Repositories

#### Checklist for New Features

Before submitting code for a new feature, verify:

- [ ] Controllers only delegate to services (no business logic)
- [ ] Each service has a single, clear responsibility
- [ ] Data classes are in their own files
- [ ] No network I/O inside `@Transactional` methods
- [ ] No self-invocation of `@Cacheable` methods
- [ ] Guard clauses used instead of nested conditionals
- [ ] Files under 300 lines (refactor if larger)
- [ ] Unit tests cover all new business logic

#### Layer Responsibilities

1. **Controller Layer** (thin)
   - Handle HTTP concerns only (request/response mapping)
   - Delegate ALL business logic to services
   - No business logic, calculations, or data transformations
   - One-liner methods that delegate to services are ideal

2. **Service Layer** (business logic)
   - Contains all business logic and calculations
   - Orchestrates domain operations
   - Returns DTOs when the mapping involves business logic
   - Handles transactions and caching

3. **Repository Layer** (data access)
   - Data access only, no business logic
   - Simple CRUD operations

```kotlin
// ✅ CORRECT: Thin controller
@GetMapping("/summary")
fun getSummary(): SummaryDto = summaryService.getSummaryDto()

// ❌ WRONG: Business logic in controller
@GetMapping("/summary")
fun getSummary(): SummaryDto {
  val data = summaryService.getData()
  val calculated = data.value * 1.1  // Business logic!
  return SummaryDto(calculated)
}
```

#### Guard Clauses

Use guard clauses to exit early and reduce nesting:

```kotlin
// ✅ CORRECT: Guard clause with early return
fun getHistoricalSummaries(page: Int, size: Int): Page<SummaryDto> {
  val summaries = repository.findAll(page, size)
  if (summaries.isEmpty) return Page.empty()

  val lookup = buildLookup(summaries.content)
  return summaries.map { it.toDto(lookup) }
}

// ❌ WRONG: Nested conditionals
fun getHistoricalSummaries(page: Int, size: Int): Page<SummaryDto> {
  val summaries = repository.findAll(page, size)
  if (summaries.isNotEmpty()) {
    val lookup = buildLookup(summaries.content)
    return summaries.map { it.toDto(lookup) }
  } else {
    return Page.empty()
  }
}
```

## Testing Standards

### Test Design Principles

- Every change must be covered by a unit test to guarantee repeatability
- Tests must be named as full English sentences stating what the object under test does
- Use backtick naming: `` `should return empty list when no transactions exist`() ``
- Each test should verify only one specific behavioral pattern
- Tests must use irregular inputs such as non-ASCII strings where appropriate
- Tests may not share object attributes between test methods
- Tests must prepare a clean state at the start rather than clean up after themselves
- Prefer fake objects and stubs over mocks when possible

### Test Structure

- Test cases should be as short as possible
- In every test, the assertion must be the last statement
- Tests may not test functionality irrelevant to their stated purpose
- Tests must close resources they use (file handlers, sockets, database connections)
- Objects must not provide functionality used only by tests
- Tests may not assert on side effects such as logging output
- The best tests consist of a single statement
- Test method names must spell "cannot" and "dont" without apostrophes

### Test Isolation

- Tests must assume the absence of an Internet connection
- Tests must not mock the file system, sockets, or memory managers
- Tests should use ephemeral TCP ports generated using appropriate library functions
- Tests should inline small fixtures instead of loading them from files
- Tests should create large fixtures at runtime rather than store them in files
- Tests may create supplementary fixture objects to avoid code duplication

### Test Reliability

- Tests must not wait indefinitely for any event; they must always stop waiting on a timeout
- Tests must retry potentially flaky code blocks
- Tests must not rely on default configurations of the objects they test
- Tests should store temporary files in temporary directories, not in the codebase directory

## Frontend (Vue/TypeScript) Standards

- Use TypeScript strict mode
- Prefer composition API over options API
- Use `const` for all variables unless reassignment is required
- Extract reusable logic into composables (`use-*.ts`)
- Keep components focused and under 200 lines
- Use proper TypeScript types, avoid `any`
