<template>
  <div class="fund-panel">
    <div class="fund-caption">
      <select
        v-model.number="reportIndex"
        class="form-select form-select-sm report-select"
        aria-label="Tuleva report month"
      >
        <option v-for="(item, index) in reports" :key="item.asOfDate" :value="index">
          {{ formatReportDate(item.asOfDate) }}
        </option>
      </select>
      <a
        class="report-link"
        :href="fundReportService.getReportUrl(TULEVA_SYMBOL, report.asOfDate)"
        target="_blank"
        rel="noopener"
      >
        Open PDF
      </a>
    </div>
    <table class="fund-table" @mouseleave="emit('leave')">
      <thead>
        <tr>
          <th>Fund</th>
          <th class="num">Weight</th>
          <th v-if="bars" aria-hidden="true"></th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in rows"
          :key="row.name"
          :class="[
            row.status,
            { dimmed: activeIndex !== null && row.slice?.index !== activeIndex },
          ]"
          @mouseenter="row.slice ? emit('hover', row.slice.index) : emit('leave')"
        >
          <td>
            <div class="fund-cell">
              <span
                class="fund-swatch"
                :style="{ backgroundColor: row.slice?.color }"
                aria-hidden="true"
              ></span>
              <span class="fund-title">
                <span class="fund-name">{{ row.name }}</span>
                <span v-if="row.status !== 'held'" class="fund-status">{{ row.status }}</span>
              </span>
              <small v-if="row.isin" class="fund-isin">{{ row.isin }}</small>
            </div>
          </td>
          <td class="num">{{ row.weight === null ? '—' : formatPercentage(row.weight) }}</td>
          <td v-if="bars" class="bar-cell" aria-hidden="true">
            <span v-if="row.slice" class="fund-track">
              <span
                class="fund-bar"
                :style="{
                  width: `${(row.slice.value / scale) * 100}%`,
                  backgroundColor: row.slice.color,
                }"
              ></span>
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { fundReportService } from '../../services/api'
import {
  CASH,
  cashWeight,
  compareFunds,
  formatReportDate,
  TULEVA_SYMBOL,
  type FundRow,
} from '../../services/fund-allocation'
import type { BreakdownRow } from '../../services/diversification-chart-service'
import type { FundReportDto } from '../../models/generated/domain-models'
import { formatPercentage } from '../../utils/formatters'

const props = defineProps<{
  reports: FundReportDto[]
  slices: BreakdownRow[]
  activeIndex: number | null
  bars?: boolean
}>()

const reportIndex = defineModel<number>('reportIndex', { required: true })

const emit = defineEmits<{
  hover: [index: number]
  leave: []
}>()

const report = computed(() => props.reports[reportIndex.value])

const scale = computed(() => Math.max(...props.slices.map(slice => slice.value)))

const rows = computed(() => {
  const slices = new Map(props.slices.map((slice, index) => [slice.label, { index, ...slice }]))
  const funds = compareFunds(report.value, props.reports[reportIndex.value + 1])
  const cash: FundRow = { isin: '', name: CASH, weight: cashWeight(report.value), status: 'held' }
  return [
    ...funds.filter(fund => fund.status !== 'dropped'),
    cash,
    ...funds.filter(fund => fund.status === 'dropped'),
  ].map(row => ({ ...row, slice: slices.get(row.name) }))
})
</script>

<style scoped>
.fund-caption {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

.report-select {
  width: auto;
}

.report-link {
  font-size: var(--text-sm);
  white-space: nowrap;
  color: var(--color-ink-soft);
  text-decoration: underline;
  text-decoration-color: var(--color-hairline-strong);
  text-underline-offset: 0.2em;
}

.report-link:hover {
  color: var(--color-ink);
  text-decoration-color: currentColor;
}

.fund-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.fund-table th {
  padding: 0 0.75rem 0.5rem;
  border-bottom: 1px solid var(--color-hairline-strong);
  font-size: var(--text-label);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-align: left;
  text-transform: uppercase;
  color: var(--color-ink-soft);
}

.fund-table td {
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid var(--color-hairline);
  vertical-align: top;
}

.fund-table tr {
  transition: opacity 0.12s ease;
}

.fund-table tr.dimmed {
  opacity: 0.35;
}

.fund-table .num {
  text-align: right;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.fund-table .bar-cell {
  width: 50%;
  vertical-align: middle;
}

.fund-track {
  display: block;
  height: 0.5rem;
  background: var(--color-surface-sunken);
  border-radius: 2px;
}

.fund-bar {
  display: block;
  height: 100%;
  border-radius: 2px;
}

.fund-cell {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  column-gap: 0.75rem;
  align-items: start;
}

.fund-swatch {
  margin-top: 0.375rem;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1px solid var(--color-hairline-strong);
}

.fund-title,
.fund-isin {
  grid-column: 2;
}

.fund-isin {
  font-size: var(--text-label);
  color: var(--color-ink-soft);
}

.fund-status {
  margin-left: 0.5rem;
  padding: 0 0.375rem;
  border-radius: var(--radius-control);
  font-size: var(--text-label);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  white-space: nowrap;
}

.new .fund-status {
  background: var(--color-notice-wash);
  color: var(--color-notice);
}

.dropped .fund-status {
  box-shadow: inset 0 0 0 1px var(--color-hairline-strong);
  color: var(--color-ink-soft);
}

.dropped .fund-name,
.dropped .num {
  color: var(--color-ink-soft);
}
</style>
