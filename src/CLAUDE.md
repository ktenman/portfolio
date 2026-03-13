# Backend Rules (Kotlin / Spring Boot)

## Gradle Version Catalogs

Dependencies are managed via Gradle Version Catalogs in `gradle/libs.versions.toml` (at project root). Use `alias(libs.library.name)` in build.gradle.kts.

If you see Java 21 warnings like "WARNING: A restricted method in java.lang.System has been called", suppress with:

```bash
export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED"
```

## Configuration

- Backend config: `src/main/resources/application.yml`
- Docker services: Multiple compose files for different environments
- CI/CD: GitHub Actions workflows in `.github/workflows/`
- Deployment: Automated via GitHub Actions with health check verification

## Kotlin Method and Class Design

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

## Exception Handling

- Exception messages must include as much context as possible
- Never swallow exceptions silently
- Use domain-specific exceptions when appropriate
- Prefer `runCatching { }.getOrElse { }` over try-catch

## Kotlin-Specific Guidelines

- Use extension functions for domain operations
- Prefer `when` expressions over if-else chains
- Use `?.let { }` and `?: return` for null handling
- Use `also`, `apply`, `let`, `run`, `with` appropriately
- Prefer functional transformations (`map`, `filter`, `fold`) over imperative loops
- Use `generateSequence` instead of while loops with mutable state
- Prefer imports over fully qualified names (use `BigDecimal` not `java.math.BigDecimal`)

## Spring Framework Guidelines

### @Cacheable Self-Invocation Problem

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

### Network I/O Outside Transactions

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

## Clean Architecture Principles

**MANDATORY FOR ALL NEW FEATURES**: Clean architecture rules MUST be strictly followed. Non-compliance will result in code review rejection.

### Enforcement Rules

1. **Controllers MUST be thin** - Maximum 1-2 lines per method, delegation only
2. **Business logic MUST live in services** - Never in controllers or repositories
3. **Services MUST have single responsibility** - One service = one domain concern
4. **Data classes MUST be in separate files** - Never nested inside services
5. **Network I/O MUST be outside transactions** - Separate persistence from external calls
6. **Caching MUST use separate services** - Avoid self-invocation problems
7. **Dependencies MUST flow inward** - Controllers → Services → Repositories

### Checklist for New Features

Before submitting code for a new feature, verify:

- [ ] Controllers only delegate to services (no business logic)
- [ ] Each service has a single, clear responsibility
- [ ] Data classes are in their own files
- [ ] No network I/O inside `@Transactional` methods
- [ ] No self-invocation of `@Cacheable` methods
- [ ] Guard clauses used instead of nested conditionals
- [ ] Files under 300 lines (refactor if larger)
- [ ] Unit tests cover all new business logic

### Layer Responsibilities

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

## Refactoring Patterns

This codebase has been systematically refactored following clean code principles. Use these patterns when making changes:

### Guard Clauses Pattern

Replace nested if-else with early returns. Check for the negative/null case first and return early:

```kotlin
fun calculateSummaryForDate(date: LocalDate): PortfolioDailySummary {
  if (isToday(date)) return calculateTodaySummary(date)

  val existingSummary = repository.findByEntryDate(date)
  if (existingSummary != null) return existingSummary

  return calculateHistoricalSummary(date)
}
```

**Key principle:** Check for the negative case (`== null`) and return early, keeping the happy path at the end without nesting.

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

### Method Extraction Pattern

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

### Guard Clauses in Clean Architecture

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

## Architecture Overview

The system follows a clean microservices architecture:

1. **API Gateway (Caddy)** - Reverse proxy with SSL termination and routing
2. **Auth Service** - OAuth 2.0 authentication (different in dev vs prod)
3. **Frontend (Vue.js SPA)** - Responsive UI with Bootstrap 5
4. **Backend API (Spring Boot)** - RESTful API with comprehensive business logic
5. **PostgreSQL** - Primary data store with optimized indexes and constraints
6. **Redis** - Multi-level caching reducing DB load by ~70%

### Architecture Documentation

PlantUML diagrams in `docs/architecture/`:

- **system-context.puml** - C4 Context diagram showing external systems and integrations (12 external APIs)
- **container-diagram.puml** - Internal containers: Web App, API, Database, Cache, Storage
- **component-diagram.puml** - Detailed API components: Controllers (11), Services (48+), Repositories
- **database-erd.puml** - Database schema with 7 core entities and relationships
- **price-update-sequence.puml** - Scheduled job execution flow with upsert idempotency
- **xirr-calculation-sequence.puml** - Parallel XIRR calculation with Kotlin Coroutines
- **frontend-architecture.puml** - Vue.js component hierarchy and state management

Additional diagrams in `screenshots/architecture.puml` (full system deployment with auth flow).

Generate with `./scripts/generate-diagrams.sh`. Edit `.puml` source files, not `.svg` outputs.

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

### External Integrations (12 systems)

- **Market Data:** FT (stocks/ETFs via HTML scraping), Binance (crypto via JSON API)
- **Trading Platforms:** Trading212, Lightyear (via Cloudflare Bypass Proxy with curl-impersonate)
- **AI Services:** OpenRouter (Claude Haiku for ETF sector classification), Google Cloud Vision (OCR)
- **Vehicle:** Auto24 (Estonian car marketplace valuation), Veego (tax reporting)
- **Other:** Telegram (notifications), MinIO (S3-compatible logo storage)
- **Infrastructure:** Cloudflare Bypass Proxy (Node.js/TypeScript, curl-impersonate for TLS fingerprint spoofing)

### FT Data Retrieval - Adaptive Scheduling

FT job uses market-phase-based adaptive scheduling (60s during market hours, 15min pre/post, 2hr off-hours, 4hr weekends). Config in `ft.adaptive-scheduling`. Key classes: `MarketPhaseDetectionService`, `FtDataRetrievalJob`.

### Performance Optimization

- **Caching**: Instrument and summary caches with automatic eviction on mutations, configurable TTL
- **Batch Processing**: Portfolio summaries in 30-item batches; XIRR may slow with 1000+ transactions
- **Database**: GIN indexes on text search, B-tree on FKs, composite index on `(instrument_id, entry_date, provider_name)`, optimistic locking

## Testing Standards

### Testing Strategy

1. **Unit Tests**: Mock external dependencies with MockK (NOT Mockito)
2. **Integration Tests**: Use `@IntegrationTest` annotation which starts PostgreSQL and Redis containers
3. **E2E Tests**: Browser-based tests with Selenide, include retry mechanism for flaky tests
4. **API Testing**: WireMock for external API mocking
5. **Frontend Tests**: Vue Test Utils with Vitest
6. **Business Logic Focus**: Tests prioritize business logic over framework functionality

### Assertion Library: Atrium

The project uses **Atrium 1.3.0-alpha-2** for all Kotlin test assertions.

**Standard Imports:**

```kotlin
import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
```

**Common Patterns:**

```kotlin
expect(value).toEqual(expected)              // Equality
expect(value).notToEqual(other)              // Inequality
expect(value).notToEqualNull()               // Null check
expect(collection).toContain(element)        // Containment
expect(collection).toContainExactly(...)     // Exact match
expect(string).notToBeEmpty()                // Empty check
expect(collection).toHaveSize(n)             // Size
expect(number).toBeGreaterThan(value)        // Comparisons
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
```

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

### Development Tips

- Use `@IntegrationTest` annotation for tests requiring database/Redis
- Scheduled jobs can be disabled with `scheduling.enabled=false`
- E2E tests generate screenshots on failure (check build artifacts)
- Backend logs: `/tmp/portfolio-backend.log`, Frontend logs: `/tmp/portfolio-frontend.log`
