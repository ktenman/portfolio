<template>
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
          <span class="row-bar" :style="barStyle(row)"></span>
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
        {{ formatBenchmarkShare(row.benchmark, row.ratio) }}
      </span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { formatPercentage } from '../../utils/formatters'
import { countryFlagUrl } from '../../utils/currency-flag'
import {
  formatBenchmarkShare,
  isFlagged,
  type BreakdownRow,
} from '../../services/diversification-chart-service'

const props = defineProps<{
  rows: BreakdownRow[]
  benchmarkLabel?: string
}>()

const scaleMax = computed(() =>
  props.rows
    .filter(row => !row.isOther)
    .reduce((max, row) => Math.max(max, row.value, row.benchmark ?? 0), 0)
)

const scaled = (value: number): number =>
  scaleMax.value === 0 ? 0 : (value / scaleMax.value) * 100

const barStyle = (row: BreakdownRow) => ({
  width: `${scaled(row.value)}%`,
  '--row-bar-color': row.color,
})

const rowTitle = (row: BreakdownRow): string => {
  const own = `${row.label} ${formatPercentage(row.value)}`
  if (row.benchmark === undefined) return own
  return `${own} · ${props.benchmarkLabel} ${row.benchmark.toFixed(2)}%`
}
</script>

<style scoped>
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
  background: var(--row-bar-color);
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
    min-width: 5.5rem;
    text-align: right;
  }
}
</style>
