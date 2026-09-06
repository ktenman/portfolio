<template>
  <div class="breakdown-panel card-shell">
    <div class="panel-header">
      <div class="breakdown-tabs" role="group" aria-label="Breakdown dimension">
        <button
          v-for="tab in TABS"
          :key="tab.key"
          class="breakdown-tab"
          :class="{ active: activeTab === tab.key }"
          :aria-pressed="activeTab === tab.key"
          type="button"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>
      <span v-if="coverage !== null" class="coverage-badge">
        Covers {{ Math.round(coverage * 100) }}% of portfolio value
      </span>
    </div>
    <div v-if="compared" class="panel-legend">
      <span class="legend-bar"></span>
      <span>This allocation</span>
      <span class="legend-tick"></span>
      <span>{{ benchmarkLabel }}</span>
    </div>
    <div class="breakdown-rows">
      <div v-for="row in rows" :key="row.label" class="breakdown-row" :title="rowTitle(row)">
        <span class="row-label">
          <img v-if="row.code" :src="countryFlagUrl(row.code)" :alt="row.code" class="row-flag" />
          <span class="row-label-text">{{ row.label }}</span>
        </span>
        <span class="row-value">{{ formatPercentage(row.value) }}</span>
        <span class="row-track" :class="{ empty: row.isOther }">
          <template v-if="!row.isOther">
            <span class="row-bar" :style="{ width: `${scaled(row.value)}%` }"></span>
            <span
              v-if="row.benchmark !== undefined"
              class="row-tick"
              :style="{ left: `${scaled(row.benchmark)}%` }"
            ></span>
          </template>
        </span>
        <span
          v-if="row.benchmark !== undefined"
          class="row-benchmark"
          :class="{ flagged: isFlagged(row.ratio) }"
        >
          {{ formatBenchmark(row) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { STORAGE_KEYS } from '../../constants'
import { formatPercentage } from '../../utils/formatters'
import { countryFlagUrl } from '../../utils/currency-flag'
import {
  compareBreakdown,
  isFlagged,
  COUNTRY_MIN_PERCENTAGE,
  INDUSTRY_MIN_PERCENTAGE,
  INDUSTRY_TOP_COUNT,
  SECTOR_MIN_PERCENTAGE,
  TOP_COUNT,
  type ComparedRow,
  type CompareOptions,
} from '../../services/diversification-chart-service'
import type { Breakdowns } from '../../composables/use-diversification-result'

const props = defineProps<{
  breakdowns: Breakdowns
  benchmark: Breakdowns | null
  benchmarkLabel?: string
  coverage: number | null
}>()

interface Tab {
  key: keyof Breakdowns
  label: string
  options: CompareOptions
}

const TABS: readonly Tab[] = [
  {
    key: 'sectors',
    label: 'Sectors',
    options: { topCount: TOP_COUNT, minPercentage: SECTOR_MIN_PERCENTAGE, withOther: true },
  },
  {
    key: 'industries',
    label: 'Industries',
    options: {
      topCount: INDUSTRY_TOP_COUNT,
      minPercentage: INDUSTRY_MIN_PERCENTAGE,
      withOther: true,
    },
  },
  {
    key: 'holdings',
    label: 'Top holdings',
    options: { topCount: TOP_COUNT, minPercentage: 0, withOther: false },
  },
  {
    key: 'countries',
    label: 'Countries',
    options: { topCount: TOP_COUNT, minPercentage: COUNTRY_MIN_PERCENTAGE, withOther: true },
  },
]

const activeTab = useLocalStorage<keyof Breakdowns>(
  STORAGE_KEYS.DIVERSIFICATION_BREAKDOWN_TAB,
  'industries'
)

const compared = computed(() => props.benchmark !== null)

const rows = computed(() => {
  const tab = TABS.find(t => t.key === activeTab.value) ?? TABS[1]
  return compareBreakdown(
    props.breakdowns[tab.key],
    props.benchmark?.[tab.key] ?? null,
    tab.options
  )
})

const scaleMax = computed(() =>
  rows.value.filter(r => !r.isOther).reduce((max, r) => Math.max(max, r.value, r.benchmark ?? 0), 0)
)

const scaled = (value: number): number =>
  scaleMax.value === 0 ? 0 : (value / scaleMax.value) * 100

const formatBenchmark = (row: ComparedRow): string => {
  const share = `${props.benchmarkLabel} ${(row.benchmark ?? 0).toFixed(2)}%`
  return row.ratio === undefined ? share : `${share} · ${row.ratio.toFixed(2)}×`
}

const rowTitle = (row: ComparedRow): string => {
  const own = `${row.label} ${formatPercentage(row.value)}`
  if (row.benchmark === undefined) return own
  return `${own} · ${props.benchmarkLabel} ${row.benchmark.toFixed(2)}%`
}
</script>

<style scoped>
.panel-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.breakdown-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.breakdown-tab {
  padding: 0.3125rem 0.75rem;
  border: 1px solid transparent;
  border-radius: var(--radius-container);
  background: transparent;
  font-size: 0.8125rem;
  color: var(--color-ink-soft);
  cursor: pointer;
  white-space: nowrap;
}

.breakdown-tab:hover {
  background: var(--color-surface-hover);
  color: var(--color-ink);
}

.breakdown-tab.active {
  border-color: var(--color-brass);
  background: var(--color-brass-wash);
  color: var(--color-brass-deep);
}

.coverage-badge {
  font-size: var(--text-label);
  color: var(--color-ink-muted);
  background: var(--color-surface-sunken);
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  white-space: nowrap;
}

.panel-legend {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  margin-bottom: 0.5rem;
  font-size: var(--text-label);
  color: var(--color-ink-muted);
}

.legend-bar {
  width: 1rem;
  height: 0.5rem;
  border-radius: 2px;
  background: var(--color-brass);
}

.legend-tick {
  width: 2px;
  height: 0.75rem;
  margin-left: 0.5rem;
  background: var(--color-ink);
}

.breakdown-row {
  display: grid;
  grid-template-columns: minmax(0, 14rem) 3.5rem minmax(0, 1fr) auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.3rem 0;
  border-bottom: 1px solid var(--color-hairline);
}

.breakdown-row:last-child {
  border-bottom: none;
}

.row-label {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  min-width: 0;
  font-size: var(--text-base);
  color: var(--color-ink-soft);
}

.row-label-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-flag {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex-shrink: 0;
}

.row-value {
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--color-ink);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.row-track {
  position: relative;
  height: 0.5rem;
  background: var(--color-surface-sunken);
  border-radius: 2px;
}

.row-track.empty {
  background: transparent;
}

.row-bar {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--color-brass);
  border-radius: 2px;
}

.row-tick {
  position: absolute;
  top: -2px;
  bottom: -2px;
  width: 2px;
  background: var(--color-ink);
  transform: translateX(-1px);
}

.row-benchmark {
  font-size: var(--text-label);
  color: var(--color-ink-muted);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.row-benchmark.flagged {
  color: var(--color-brass-deep);
  font-weight: 600;
}

@media (max-width: 639px) {
  .breakdown-row {
    grid-template-columns: minmax(0, 1fr) auto;
    row-gap: 0.25rem;
  }

  .row-track {
    grid-column: 1;
  }

  .row-benchmark {
    grid-column: 2;
    justify-self: end;
  }
}
</style>
