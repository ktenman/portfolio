<template>
  <div class="breakdown-panel card-shell">
    <div class="panel-header">
      <div class="breakdown-tabs" role="group" aria-label="Breakdown dimension">
        <button
          v-for="tab in TABS"
          :key="tab.key"
          class="breakdown-tab"
          :class="{ active: currentTab.key === tab.key }"
          :aria-pressed="currentTab.key === tab.key"
          type="button"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
        <template v-if="benchmarkLabel">
          <span class="platform-separator" aria-hidden="true"></span>
          <label class="compare-switch compare-toggle">
            <input v-model="compare" type="checkbox" role="switch" class="compare-input" />
            <span class="compare-track" aria-hidden="true"></span>
            <span class="compare-label">
              <span class="compare-prefix">vs</span>
              {{ benchmarkLabel }}
            </span>
          </label>
        </template>
      </div>
      <div v-if="compared" class="panel-legend">
        <span class="legend-bar"></span>
        <span>This allocation</span>
        <span class="legend-tick"></span>
        <span>{{ benchmarkLabel }}</span>
      </div>
    </div>
    <div class="breakdown-rows">
      <div
        v-for="row in rows"
        :key="row.isOther ? '__other' : row.label"
        class="breakdown-row"
        :title="rowTitle(row)"
      >
        <span class="row-label">
          <img v-if="row.code" :src="countryFlagUrl(row.code)" :alt="row.code" class="row-flag" />
          <span class="row-label-text">{{ row.label }}</span>
        </span>
        <span class="row-value">{{ formatPercentage(row.value) }}</span>
        <span class="row-track" :class="{ empty: row.isOther }">
          <template v-if="!row.isOther">
            <span class="row-bar" :style="{ width: `${scaled(row.value)}%` }"></span>
            <span
              v-if="showsBenchmark(row)"
              class="row-tick"
              :style="{ left: `${scaled(row.benchmark)}%` }"
            ></span>
          </template>
        </span>
        <span
          v-if="showsBenchmark(row)"
          class="row-benchmark"
          :class="{ flagged: isFlagged(row.ratio) }"
        >
          {{ formatBenchmarkShare(row.benchmark, row.ratio) }}
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
  formatBenchmarkShare,
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
    label: 'Holdings',
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

const compare = useLocalStorage(STORAGE_KEYS.BENCHMARK_COMPARE, true)

const compared = computed(() => compare.value && props.benchmark !== null)

const currentTab = computed(() => TABS.find(t => t.key === activeTab.value) ?? TABS[1])

const rows = computed(() =>
  compareBreakdown(
    props.breakdowns[currentTab.value.key],
    compared.value ? (props.benchmark?.[currentTab.value.key] ?? null) : null,
    currentTab.value.options
  )
)

const scaleMax = computed(() =>
  rows.value.filter(r => !r.isOther).reduce((max, r) => Math.max(max, r.value, r.benchmark ?? 0), 0)
)

const scaled = (value: number): number =>
  scaleMax.value === 0 ? 0 : (value / scaleMax.value) * 100

const showsBenchmark = (row: ComparedRow): row is ComparedRow & { benchmark: number } =>
  row.benchmark !== undefined && !(currentTab.value.key === 'holdings' && row.benchmark === 0)

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
  align-items: center;
  gap: 0.25rem;
}

.breakdown-tabs .platform-separator {
  margin: 0 0.25rem;
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

.panel-legend {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: var(--text-label);
  color: var(--color-ink-muted);
  white-space: nowrap;
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
  .breakdown-tabs {
    flex-wrap: nowrap;
    width: 100%;
    gap: 0.375rem;
  }

  .breakdown-tab {
    padding: 0.3125rem 0;
    border: 0;
    border-bottom: 0.125rem solid transparent;
    border-radius: 0;
    font-size: var(--text-label);
  }

  .breakdown-tab.active,
  .breakdown-tab:hover {
    background: transparent;
  }

  .breakdown-tabs .platform-separator {
    display: none;
  }

  .compare-prefix {
    display: none;
  }

  .compare-toggle {
    margin-left: auto;
  }

  .compare-switch {
    gap: 0.25rem;
    padding-inline: 0.125rem;
    font-size: var(--text-label);
  }

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
    min-width: 5.5rem;
    text-align: right;
  }
}

@media (max-width: 359px) {
  .breakdown-tabs {
    flex-wrap: wrap;
  }

  .compare-toggle {
    margin-left: 0;
  }
}
</style>
