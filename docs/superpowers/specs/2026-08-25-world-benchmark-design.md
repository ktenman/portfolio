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
