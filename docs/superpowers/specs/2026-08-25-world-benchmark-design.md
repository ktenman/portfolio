# World Benchmark Selector

Date: 2026-08-25
Status: Approved

## Problem

The summary chart's performance mode (PR #1776, branch
`feature/1774-benchmark-comparison`, unmerged) compares the portfolio only to
the S&P 500 via VUAA. The user wants a world-stocks benchmark as well. The
work stacks onto the same branch and PR.

## Scope decisions (user-approved)

1. **Benchmark selector, not overlay:** one benchmark visible at a time. The
   toggle becomes three buttons: `€ | % vs S&P 500 | % vs World`. Rationale:
   `buildPerformanceSeries` anchors both lines at the first summary date that
   has a benchmark price at-or-before it — two benchmarks with different
   inception dates cannot share one chart honestly.
2. **World fund: `VWCE:GER:EUR`** (Vanguard FTSE All-World, instrument id 11),
   user-confirmed. `SPPW:GER:EUR` (SPDR MSCI World) is the exists-fallback,
   mirroring the SP500 chain `VUAA → SPYL`. WEBN rejected: prices only since
   2024-06-26. SPPW as primary rejected: developed-markets only. IS3S: value
   tilt. EXUS: ex-US.
3. **No merge of #1776 first** — the user chose to stack; PR #1776 grows.

## Current state (evidence)

- `BenchmarkSeriesService` holds `BENCHMARK_SYMBOLS = listOf("VUAA:GER:EUR",
"SPYL:GER:EUR")`; `getSeries(range)` takes the first symbol whose instrument
  exists, merges FT and LIGHTYEAR rows per date (`sortedWith(entryDate,
providerName)` then `distinctBy(entryDate)` — FT wins ties), returns
  `List<BenchmarkPointDto>`.
- `PortfolioSummaryController` exposes `GET /api/portfolio-summary/benchmark?range=`.
- `ui/components/portfolio/chart-mode-toggle.vue` declares
  `ChartMode = 'value' | 'performance'` and renders two buttons; the mode
  persists via `useLocalStorage(STORAGE_KEYS.SUMMARY_CHART_MODE)` in
  `portfolio-summary.vue`, which falls back to `value` when the benchmark
  series is empty (`activeMode` computed).
- `ui/composables/use-portfolio-summary-query.ts` fetches one benchmark series
  per range (`queryKey: ['portfolio-summary', 'benchmark', rangeKey]`).
- `portfolio-chart.vue` picks performance rendering by `'benchmarkValues' in
props.data` and hardcodes the legend label "S&P 500".
- DB coverage: VWCE has FT prices since 2019-07-25 plus LIGHTYEAR since
  2019-08-01; portfolio summaries start 2020-01-02 → the World line covers
  every range including MAX with no missing-anchor gap.
- #1776 was never deployed → renaming the persisted `'performance'` mode value
  is free; no localStorage migration.

## Design

### Backend

- `BenchmarkIndex` enum with values `SP500`, `WORLD` in `domain/Enums.kt`.
- `BENCHMARK_SYMBOLS` becomes `Map<BenchmarkIndex, List<String>>`:
  `SP500 → ["VUAA:GER:EUR", "SPYL:GER:EUR"]`,
  `WORLD → ["VWCE:GER:EUR", "SPPW:GER:EUR"]`.
- `getSeries(range: TimeRange, index: BenchmarkIndex)` resolves the symbol
  list by index; the merge/dedup logic is unchanged.
- Controller parameter `@RequestParam(defaultValue = "SP500") index:
BenchmarkIndex` — existing URLs without the param keep working.
- `BenchmarkIndex` joins the TypeScript-generation `classes` list in
  `build.gradle.kts`.

### Frontend

- `ChartMode = 'value' | 'sp500' | 'world'` (rename of `'performance'`).
- `use-portfolio-summary-query.ts` fetches both benchmark series per range —
  two small requests keyed `['portfolio-summary', 'benchmark', rangeKey,
index]`; exposes `sp500Points` and `worldPoints`.
- `chart-mode-toggle.vue` renders the World button only when the world series
  is non-empty; labels `€`, `% vs S&P 500`, `% vs World`.
- `portfolio-summary.vue` feeds the selected index's points into
  `usePerformanceChart` — the series math is unchanged; `activeMode` falls
  back to `value` when the selected mode's series is empty.
- `portfolio-chart.vue` receives the benchmark label ("S&P 500" or "World")
  with the chart data instead of hardcoding it.

### Testing

- Backend: `BenchmarkSeriesServiceTest` covers index→symbol selection and the
  WORLD fallback chain; controller test covers the default and an explicit
  `index=WORLD`.
- Frontend: toggle renders three states; composable fetches both series; mode
  falls back to `value` when the selected series is empty; legend label
  switches with the index.
- Visual: the summary fixture stubs the WORLD benchmark route; affected
  baselines re-recorded.

## Addendum (2026-08-25): three-line overlay supersedes the selector

User decision: show Portfolio, S&P 500 and World together in one `%` mode; the
toggle collapses to `€ | %`. The selector's rationale (scope decision 1) does
not bite for these funds: both VUAA and VWCE have prices before the first
portfolio summary (2020-01-02), so all three lines share one anchor on every
range including MAX.

- Backend unchanged: both `index=SP500` and `index=WORLD` endpoint variants
  stay; the frontend already fetches both series per range.
- `buildPerformanceSeries(summaries, sp500, world)` returns
  `{ portfolioValues, sp500Values, worldValues }`. The anchor is the first
  summary date at which every non-empty benchmark has a price at-or-before it;
  an empty benchmark series yields all-null values and does not constrain the
  anchor; both empty (or no common anchor) yields all nulls.
- `PerformanceChartData` carries `sp500Values`/`worldValues`; `benchmarkLabel`
  is gone. The chart hardcodes legend labels Portfolio, S&P 500, World and
  omits a benchmark dataset with no non-null point.
- `ChartMode = 'value' | 'performance'`; the toggle renders `€` and `%` and
  drops the `worldAvailable` prop; the toggle still shows when either
  benchmark series is non-empty. Persisted `'sp500'`/`'world'` values are
  normalized to `'performance'` on load (never deployed, dev browsers only).
- Colors follow the euro chart's rotation: Portfolio `CHART_COLORS[0]`,
  S&P 500 `CHART_COLORS[1]`, World `CHART_COLORS[3]`.
- Visual: the World fixture gets a growth profile distinct from S&P (rebased
  percentages of a uniform 0.9x multiple are pixel-identical); summary
  baselines re-record for the two-button toggle; a new states capture pins the
  three-line percent view.
- `%` mode is offered only while the platform selection covers every platform,
  because a proper subset is served sampled to 60 dates and compounding across
  those multi-week gaps would let deposits inside a gap inflate the portfolio
  line; a filtered view resolves the displayed mode to `€` without overwriting
  the persisted mode.

## Addendum 2 (2026-08-25): independent benchmark toggles supersede the overlay

User decision: the fixed three-line overlay lost the ability to view one
benchmark alone, so the two `%` buttons become independent toggles. One active
benchmark draws two lines, both draw three, none (or clicking `€`) restores the
euro chart.

- Backend unchanged: both `index=SP500` and `index=WORLD` endpoint variants
  stay; the frontend still fetches both series per range.
- `useBenchmarkSelection()` replaces `useChartMode()`: the selection is a
  `BenchmarkKey[]` (`'sp500' | 'world'`) persisted as a CSV string under the
  same storage key. The getter filters a canonical key list against the stored
  string, so order is stable (S&P 500 before World regardless of click order)
  and any legacy value (`'value'`, `'performance'`, junk) normalizes to the
  empty selection.
- `buildPerformanceSeries(summaries, benchmarks: BenchmarkPointDto[][])`
  generalizes to N benchmarks and returns
  `{ portfolioValues, benchmarkValues: (number | null)[][] }`. The anchor is
  the first summary date at which every selected benchmark has a price
  at-or-before it, so all lines start at 0% together; each benchmark rebases
  against its own anchor close. For a single benchmark this is numerically
  identical to the selector implementation, so the recorded sp500-only
  baseline is unchanged.
- `PerformanceChartData` carries `benchmarks: { label, values }[]`; the chart
  maps one dataset per entry with a fixed color per label (S&P 500
  `CHART_COLORS[1]`, World `CHART_COLORS[3]`) and `resolveChartMode` dissolves
  into `performanceChartData ?? processedChartData`.
- The toggle keeps the three buttons `€ | % vs S&P 500 | % vs World`; the `€`
  button reads active when the selection is empty and clicking it clears the
  selection; each `%` button toggles its key independently.
- The platform gate is unchanged: benchmarks activate only while the platform
  selection covers every platform; a filtered view falls back to `€` without
  overwriting the persisted selection.
- Visual: the existing `€` and sp500-only captures stay byte-identical; a new
  states capture (`summary-benchmark-both`) pins the three-line view.
