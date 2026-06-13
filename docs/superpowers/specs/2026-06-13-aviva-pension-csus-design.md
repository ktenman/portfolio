# Aviva Pension: GBP/EUR Conversion, Fee Modeling, and CSUS Look-Through

Date: 2026-06-13
Branch: `feature/aviva-pension-gbp-eur`
Status: Approved (design forks resolved 2026-06-13)

## Problem

The branch already adds an Aviva workplace pension as a tracked instrument
(`GB00B0ZDNB53:GBP`), priced in GBP from FT and converted to EUR via ECB daily
reference rates. Three things remain before it is trustworthy and complete:

1. The GBP-quoted-in-pounds assumption that the whole conversion rests on is not
   pinned by any test. If FT ever switched to pence, conversion would silently
   produce values ~100x too large and every test would stay green.
2. `EcbCsvParser` fails silently when the ECB CSV header changes shape, so a
   format change would quietly stop the rate backfill and surface later as an
   unrelated "no exchange rate found" error.
3. The pension has no holdings look-through. The user wants the Aviva pot to show
   sector/holding diversification using the iShares MSCI USA ETF (CSUS) as a
   daily-disclosed proxy for the underlying BlackRock US Equity Index tracker.

## Context: what already exists and reconciles

The migration transactions reconcile cleanly against all nine Aviva documents:

- `2020-01-02` BUY of £3,459.86 = the 2019 year-end value (the pre-2020 Investec
  American Franchise history folded into one opening position; the fund became
  the BlackRock US Equity tracker during 2020).
- Monthly BUYs match the statement contribution totals (employee + employer):
  2020 £420 → £466.66, 2021 £466.66 → £500 → £207.69, last payment 2021-10-20,
  then £0 forever — matching the November 2021 payments-stopped letter.
- Six fee SELLs map to each statement's "costs and charges" line:
  £38.09 / £112.89 / £100.50 / £120.19 / £171.42 / £152.01.

Statement-end closing values (reconciliation anchors, GBP):

| Statement date | Closing value |
| --- | --- |
| 2019-12-31 | £3,459.86 |
| 2020-12-31 | £9,833.17 |
| 2022-02-28 | £16,339.94 |
| 2023-02-28 | £16,763.28 |
| 2024-02-29 | £20,717.23 |
| 2025-02-28 | £24,236.38 |
| 2026-02-28 | £26,763.18 |

The conversion direction is correct: `priceEUR = priceGBP / rate`, where `rate`
is ECB GBP-per-EUR (~0.85). FT returns the fund price in pounds (~11.7), not
pence. No `/100` is needed.

## Decisions (resolved forks)

- **Fee model: keep the near-zero-price SELL.** A management fee is a value leak
  with no cashflow to the investor, so units vanishing at ~£0 is correct for
  portfolio totals and XIRR. The only casualty is the Aviva line's per-instrument
  Realized P/L, which shows a phantom loss equal to the cost basis of the vanished
  units. This is documented, not fixed — a dedicated `FEE` transaction type was
  rejected as a large blast radius across a working system for a cosmetic gain,
  and selling fee units at real price was rejected because the proceeds would
  inflate XIRR.
- **CSUS look-through: attach holdings to the Aviva instrument.** Fetch
  BlackRock's daily CSUS holdings CSV and populate the existing Aviva instrument's
  `EtfPosition`/`EtfHolding` rows directly via the existing
  `EtfHoldingService.saveHoldings(...)` path. No separate CSUS instrument, no
  price series for CSUS. The diversification breakdown auto-includes the Aviva
  instrument because it is an `FT`-provider instrument with positions and
  net-positive units — no allowlist or config entry is required.

## Architecture

Three independent units of work, smallest first. Each is shippable on its own.

### Unit 1 — Pin the GBP-unit assumption (Gate 0)

`HistoricalPricesServiceTest` stubs `convertDailyPricesToEur` (passthrough), and
`CurrencyConversionServiceTest` tests the divide-by-rate math but never the
magnitude. Add a focused, hermetic test exercising the real
`CurrencyConversionService` with a mocked repository returning a realistic
GBP-per-EUR rate (e.g. 0.85) and asserting an FT pounds close (11.74) converts to
~13.81 EUR — proving pounds, not pence, and no `/100`. This is the test that
fails loudly if FT ever changes its quote unit.

Also add a migration-structure guard (extending `AvivaPensionMigrationIT`) that
asserts, against the applied Testcontainers migration:
- net remaining units = sum(BUY qty) − sum(SELL qty), and it is > 0;
- exactly six SELL rows priced at `0.00000001`, one per statement date;
- the expected BUY row count.

This freezes the reconciled migration so an accidental edit can't silently
drift the model.

### Unit 2 — Fail loud in `EcbCsvParser`

Change `parse()` so a missing `TIME_PERIOD` or `OBS_VALUE` header throws
`IllegalStateException` with the offending header line in the message, instead of
returning `emptyList()`. Blank input (`< 2` lines) still returns `emptyList()`
(that is a legitimate empty response, not a format break). Add a test asserting
the throw on a wrong header; keep the existing happy-path, blank, blank-value,
and malformed-date tests green.

### Unit 3 — CSUS holdings, attached to the Aviva instrument

Mirror the existing ECB-client and Trading212-holdings patterns. New package
`ee.tenman.portfolio.blackrock`.

```
BlackRockHoldingsClient  (Feign)   -> GET BlackRock .ajax CSV, browser User-Agent
BlackRockCsvParser       (object)  -> CSV string -> List<BlackRockHolding>
CsusHoldingsService      (@Service)-> client + parser -> List<HoldingData> (ranked)
CsusHoldingsRetrievalJob (@ScheduledJob) -> idempotent fetch + saveHoldings
```

- **`BlackRockHoldingsClient`** — `@FeignClient(name = "blackRockHoldingsClient",
  url = "\${blackrock.url:https://www.blackrock.com}")`. One `@GetMapping` to
  `/uk/individual/products/{productId}/x/1472631233320.ajax` with request params
  `fileType=csv`, `fileName={fund}_holdings`, `dataType=fund`, and a declarative
  browser `User-Agent` header on the mapping. Returns the raw CSV `String`
  (Feign string decoding is already configured on this branch). Add
  `blackrock.url` to `application.yml`.
- **`BlackRockCsvParser`** — mirrors `EcbCsvParser`. The CSV has a metadata
  preamble; the real header row starts with `Ticker,`. Skip to that line, parse
  with a header-indexed split, keep rows where `Asset Class == Equity`, and
  extract Ticker, Name, Sector, Weight (%), and Location (country name). Fail
  loud (throw) if the `Ticker,` header is never found — same rationale as Unit 2.
  Skip individual malformed rows with a warn, like the ECB parser.
- **`CsusHoldingsService`** — `@Retryable`, calls the client, parses, sorts by
  weight descending, and maps to `HoldingData(name, ticker, sector, weight, rank,
  countryName=Location)`. Country code and logos are left to the existing
  classification/enrichment pipeline; sector comes straight from BlackRock's GICS
  column.
- **`CsusHoldingsRetrievalJob`** — `@ScheduledJob` implementing `Job`, mirroring
  `Trading212HoldingsRetrievalJob`: dual `@Scheduled` (initial delay + daily
  cron), `JobExecution` tracking via `jobTransactionService.saveJobExecution`,
  idempotency via `etfHoldingService.hasHoldingsForDate("GB00B0ZDNB53:GBP",
  today)`, persist via `etfHoldingService.saveHoldings("GB00B0ZDNB53:GBP", today,
  holdings)`, then `etfBreakdownService.evictBreakdownCache()`. The CSUS product
  id (`253740`) and fund label (`CSUS`) are job constants — single-use, no config
  abstraction.

Data flow:

```
BlackRock .ajax CSV
  -> BlackRockHoldingsClient (raw CSV string)
  -> BlackRockCsvParser (List<BlackRockHolding>)
  -> CsusHoldingsService (List<HoldingData>, ranked by weight)
  -> EtfHoldingService.saveHoldings("GB00B0ZDNB53:GBP", today, holdings)
       -> EtfHoldingPersistenceService upserts EtfHolding + EtfPosition rows
  -> diversification breakdown reads Aviva positions automatically (FT provider)
```

## Error handling

- ECB parser and BlackRock parser: fail loud on header/format breaks; skip
  individual malformed data rows with a warn (a single bad row must not lose the
  whole feed).
- `CsusHoldingsRetrievalJob`: wrap work in try/catch/finally, record
  `JobStatus.FAILURE` with the message, never throw out of the scheduled method —
  identical to the Trading212 job.
- Network I/O stays outside transactions: the Feign fetch happens in the job/
  service, and only `EtfHoldingPersistenceService.saveHoldings` is `@Transactional`
  — matching the existing holdings pipeline.

## Testing

- **Unit 1:** hermetic GBP-magnitude conversion test (real service, mocked repo);
  migration-structure guard IT.
- **Unit 2:** ECB parser fail-loud test; existing parser tests stay green.
- **Unit 3:** `BlackRockCsvParser` tests with an inlined CSV fixture (preamble +
  `Ticker,` header + equity rows + a non-equity row that must be skipped + a
  malformed row); `CsusHoldingsService` test (mocked client → ranked
  `HoldingData`); `CsusHoldingsRetrievalJob` test (idempotency skip path + save
  path, with mocks). All hermetic — no live network, fixtures inlined.

CI gate to satisfy locally: `./gradlew ktlintCheck detekt` plus the relevant
`./gradlew test --tests` runs.

## Scope / non-goals

- All work lands on `feature/aviva-pension-gbp-eur` (no new branch).
- No `FEE` transaction type; no schema change for fees.
- No CSUS instrument and no CSUS price series.
- No diversification-config or allowlist change.
- No refactoring beyond the two targeted fail-loud changes.
