# Portfolio chart time-range selector

**Date:** 2026-08-14
**Status:** Design approved, ready for implementation plan

## Problem

The Portfolio Summary chart always shows the same window: page 0 of the historical
endpoint at `size=186`, roughly six months, plus today's live point merged from
`/current`. There is no way to look at a week or at the whole history.

The requested control is a chip row under the chart: `1W · 1M · 6M · YTD · 1Y · 2Y · 3Y · MAX`.
Selecting a range must issue a real backend request for that window, not slice data
already in the browser.

## Decisions

**Preset enum on the wire, not `from`/`to` dates.** The window vocabulary is closed and
UI-driven, so a token (`?range=1Y`) is the conventional shape for chart APIs — Yahoo
Finance, TradingView and Lightyear all use it. Three concrete reasons over free-form
dates: the Redis key space stays bounded at 8 ranges × platform-set instead of minting a
fresh uncacheable key for every distinct day a user visits; the calendar math (YTD, leap
years, "one year before Feb 29") lives in one testable place on the server instead of
drifting between Kotlin and TypeScript; and pathological windows become unrepresentable.

**The range determines resolution, not just the window.** `SummaryBatchProcessorService.calculateSummaries`
recomputes a portfolio summary per date from the transaction list, including an XIRR, at
a measured ~3.4 ms per date. MAX spans 2,416 days = **8.5 s measured**, against the 10 s
axios timeout in `ui/utils/http-client.ts`, to produce points the frontend immediately
samples down to 61. So the server computes **at most 366 dates, evenly spaced across the
window**:

| Range | Days in window | Dates computed       | Estimated cold |
| ----- | -------------- | -------------------- | -------------- |
| 1W    | 7              | 7                    | <0.1 s         |
| 1M    | 31             | 31                   | ~0.1 s         |
| 6M    | 181            | 181                  | ~1.8 s         |
| YTD   | ~225           | ~225                 | ~2.0 s         |
| 1Y    | 365            | 365                  | ~2.5 s         |
| 2Y    | 730            | 366 (every 2nd day)  | ~2.5 s         |
| 3Y    | 1095           | 366 (every 3rd day)  | ~2.5 s         |
| MAX   | 2416           | 366 (every ~7th day) | ~2.5 s         |

Every range now costs at most what 1Y costs, and every range at or under the cap keeps
every single day.

The default view shifts slightly: six calendar months is 181 days, where the old fixed
page was 186. The 186 was an arbitrary legacy page size, and the reason to preserve it —
reusing the already-warmed `size=186` cache entry — does not survive the move to a
separate endpoint, since the series cache key is distinct from the historical one either
way. `minusMonths(6)` is the honest definition of 6M, so the chart starts five days later
than it does today.

That sampling is safe because `calculateSummaries` walks the sorted transaction list
forward per date — the dates must be sorted and ascending, **not consecutive**.

**A new endpoint rather than a `range` parameter on `/historical`.** The table's paginated
path is untouched and cannot regress; the response is a flat list instead of a `Page`; and
the previous-day lookup is skipped entirely, because the chart never reads
`totalProfitChange24h` and a 24-hour delta between non-consecutive sampled points would be
a lie.

**No scheduled cache warming.** `PLATFORM_SUMMARY_CACHE` has a 1-hour TTL
(`RedisConfiguration.kt:22-23`) and is otherwise evicted only by manual recalculation and
the 06:30 `DailyPortfolioXirrJob` — the 2-minute `CurrentDaySummaryRefreshJob` uses
`@CachePut` on a different cache and does not evict. So each range key goes cold about
once an hour and the first user of that hour pays ~2.5 s. That is accepted. A periodic
re-warm was considered and rejected as a permanent moving part bought against an
occasional one-off wait.

Indexes were considered and do not apply: the cost is CPU, not I/O. Transactions are
fetched once per call from `TRANSACTION_CACHE` and the per-date loop does essentially no
database round-trips.

## Out of scope

Per-platform daily summaries are not materialised — only global ones are, in
`portfolio_daily_summary`. Persisting per-platform rows would turn every range into a
table read and delete the sampling cap and the cold-start wait outright. That is a schema
plus backfill plus job plus eviction-rules project and does not belong in this change.

The table keeps its 186-row `useInfiniteQuery` and infinite scroll exactly as they are.
The chart's frontend sampling in `use-portfolio-chart.ts` (61 points desktop, 30 mobile)
is unchanged and continues to apply on top of whatever the server returns.

## Backend

### `domain/SummaryRange.kt`

```kotlin
enum class SummaryRange(
  @get:JsonValue val code: String,
) {
  ONE_WEEK("1W"),
  ONE_MONTH("1M"),
  SIX_MONTHS("6M"),
  YTD("YTD"),
  ONE_YEAR("1Y"),
  TWO_YEARS("2Y"),
  THREE_YEARS("3Y"),
  MAX("MAX"),
  ;

  fun startDate(today: LocalDate): LocalDate?

  fun dates(firstDate: LocalDate, today: LocalDate): List<LocalDate>

  companion object {
    const val MAX_POINTS = 366

    fun from(code: String): SummaryRange

    fun <T> sample(items: List<T>, maxPoints: Int = MAX_POINTS): List<T>
  }
}
```

`startDate` returns `today.minusWeeks(1)`, `minusMonths(1)`, `minusMonths(6)`,
`withDayOfYear(1)`, `minusYears(1)`, `minusYears(2)`, `minusYears(3)`, and `null` for
`MAX`. Null means unbounded — one meaning, one null.

`dates` builds every day from `max(firstDate, startDate(today))` through `today.minusDays(1)`
ascending, then applies `sample`. It returns an empty list when `firstDate` is after
yesterday.

`sample` uses the same index formula as the frontend's `sampleDataPoints`:
`step = (n - 1) / (maxPoints - 1)`, `index = round(i * step)`. Both endpoints are always
included. Lists at or under `maxPoints` are returned unchanged.

Declaration order is the chip order, so the generated TypeScript enum needs no separate
ordering.

### `configuration/SummaryRangeConverter.kt`

A `@Component` implementing `Converter<String, SummaryRange>` delegating to
`SummaryRange.from`. Spring Boot registers `Converter` beans into the MVC conversion
service automatically. Jackson's `@JsonValue` covers response serialisation and TypeScript
generation but does not apply to query-parameter binding, hence the converter. An
unrecognised code throws, producing a 400.

### `service/summary/SummaryService.kt`

```kotlin
fun getSeriesForPlatforms(platforms: List<Platform>, range: SummaryRange): List<PortfolioDailySummary>
```

Loads and sorts the platform-filtered transactions, takes `firstDate` from the earliest
transaction, calls `range.dates(firstDate, LocalDate.now(clock))`, and passes that date
list to the existing `calculateSummariesForDates`. Returns an empty list when there are no
transactions.

```kotlin
fun getSeries(range: SummaryRange): List<PortfolioDailySummary>
```

The unfiltered path reads stored rows via
`portfolioDailySummaryRepository.findAllByEntryDateBetween(start, yesterday)` — where
`start` is `range.startDate(today)` or the earliest stored entry date for `MAX` — then
applies `SummaryRange.sample` to the returned rows. Sampling the fetched list rather than
the requested dates means gaps in stored history simply yield fewer points instead of
missing lookups.

### `service/summary/PlatformSummaryCacheService.kt`

```kotlin
@Cacheable(
  value = [PLATFORM_SUMMARY_CACHE],
  key = "'platform-series-' + #root.target.platformKey(#platforms) + '-' + #range.name()",
  unless = "#result.isEmpty()",
)
fun getSeriesForPlatforms(platforms: List<Platform>, range: SummaryRange): List<PortfolioDailySummary>
```

Caching lives in this separate service, as the existing platform summary methods do, so
the proxy is not bypassed by self-invocation.

### `service/summary/PortfolioSummarySeriesService.kt`

Owns range resolution and DTO mapping so the controller stays thin: picks the filtered or
unfiltered cache service based on whether platforms were supplied, and maps
`PortfolioDailySummary` to `PortfolioSummaryDto` with `totalProfitChange24h = null`.

### `controller/PortfolioSummaryController.kt`

```kotlin
@GetMapping("/series")
@Loggable
fun getSummarySeries(
  @RequestParam(defaultValue = "6M") range: SummaryRange,
  @RequestParam(required = false) platforms: List<String>?,
): List<PortfolioSummaryDto> = seriesService.getSeries(range, Platform.parseList(platforms))
```

### `configuration/PortfolioSummaryWarmup.kt`

Add `/api/portfolio-summary/series?range=6M` to `BASE_PATHS` and the platform-filtered
equivalent to `warmupPaths()`. This is two entries in arrays that already exist, still
fired once from `ApplicationReadyEvent`. No new scheduled job.

### `build.gradle.kts`

Add `"ee.tenman.portfolio.domain.SummaryRange"` to the TypeScript generator `classes` list
so the enum reaches `ui/models/generated/domain-models.ts`.

If the generator emits `ONE_YEAR = 'ONE_YEAR'` rather than honouring `@JsonValue`, the
frontend chip labels fall back to a local label map keyed by the generated enum; the wire
format is unaffected either way.

## Frontend

### `ui/constants/api.ts`

`PORTFOLIO_SUMMARY_SERIES: '/portfolio-summary/series'`

### `ui/constants/storage-keys.ts`

`SUMMARY_CHART_RANGE: 'portfolio_summary_chart_range'`

### `ui/services/portfolio-summary-service.ts`

```ts
getSeries: (range: SummaryRange, platforms?: string[]) =>
  httpClient.get<PortfolioSummaryDto[]>(API_ENDPOINTS.PORTFOLIO_SUMMARY_SERIES, {
    params: { range, ...(platforms?.length ? { platforms } : {}) },
  })
```

### `ui/composables/use-chart-range.ts`

Wraps `useLocalStorage(STORAGE_KEYS.SUMMARY_CHART_RANGE, SummaryRange.SIX_MONTHS)` and
exposes `selectedRange` plus a `selectRange` setter. Per the frontend rules, no direct
`localStorage` access.

### `ui/components/portfolio/chart-range-filter.vue`

Presentational chip row modelled on `components/shared/platform-filter.vue`: same
`.platform-btn` / `:class="{ active }"` pattern, `selected` prop, `select` emit, no data
fetching of its own.

### `ui/composables/use-portfolio-summary-query.ts`

Takes the range ref as a second argument and adds one query:

```ts
useQuery({
  queryKey: ['portfolio-summary', 'series', platformsKey, selectedRange],
  queryFn: () => portfolioSummaryService.getSeries(selectedRange.value, activePlatforms.value),
  placeholderData: keepPreviousData,
  enabled: isAuthenticated,
})
```

`keepPreviousData` is load-bearing: without it the chart blanks and the page jumps for the
~2.5 s of a cold range switch. The composable returns `chartSummaries`, the series merged
with the existing `currentSummary` so today's live point still appears, exactly as
`summaries` does today. The existing `useInfiniteQuery` and its return values are
unchanged.

### `ui/components/portfolio-summary.vue`

Renders `<chart-range-filter>` directly below `<portfolio-chart>` and above the table, and
feeds `chartSummaries` rather than `summaries` into `usePortfolioChart`. Table wiring,
`useInfiniteScroll` and `sortedItems` are untouched.

## Error handling

A malformed `range` produces a 400 from the converter. The frontend cannot send one — the
value is typed against the generated enum.

A failed series request must not blank the page. `viewState` stays driven by the table
query alone, so a series error leaves the last good chart on screen via `keepPreviousData`
while the rest of the tab keeps working.

An empty series — a 1W window with no transactions in it — renders the chart from today's
`/current` point alone. `usePortfolioChart` already returns null for a zero-length list, so
the chart simply does not render.

## Testing

**Kotlin.** `SummaryRangeTest` covers `startDate` per constant, YTD across a year boundary,
MAX returning null, the 366 cap with both endpoints retained, sub-cap windows returned
unchanged, and an empty result when the first transaction date is after yesterday. An
integration test hits `/series` for a filtered and an unfiltered request and asserts the
returned point count and date bounds. Atrium assertions, backtick sentence names, "dont"
and "cannot" spelled without apostrophes.

**Vitest.** `use-chart-range.test.ts` covers the default and persistence round-trip;
`chart-range-filter.test.ts` covers rendering eight chips in declaration order, marking the
selected one active, and emitting on click.

**Visual regression.** `ui/tests/visual/summary-fixture.ts` must stub the new `/series`
route or every `/`-rendering test loses its chart. Baselines to re-record across all
projects: `route-summary.png`, `modal-confirm.png`, `toast-success|error|info|warning.png`,
`state-loading.png`.

**Commands.** `./gradlew test`, then `npm run lint-format` and `npm test` in `ui/`.

## Success criteria

- The chip row renders under the chart with 6M selected on a fresh browser profile, and the
  chart shows a 181-day window plus today's live point.
- Clicking any chip issues exactly one `/series` request with that range and redraws the
  chart from the response.
- The selection survives a page reload.
- MAX returns in well under the 10 s axios timeout on a cold cache.
- The table's rows, sorting and infinite scroll are unchanged by any chip selection.
