<template>
  <button
    class="report-btn"
    type="button"
    popovertarget="fund-report"
    aria-label="Tuleva monthly report"
    title="Tuleva monthly report"
  >
    <svg
      width="12"
      height="12"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path d="M4 1.75h5.25L12.5 5v9.25H4z" />
      <path d="M9 1.75V5.25h3.5" />
      <path d="M6.25 8.5h4M6.25 11h4" />
    </svg>
  </button>
  <section id="fund-report" class="report-popover" popover aria-label="Tuleva monthly report">
    <header class="report-header">
      <div>
        <h3 class="report-title">{{ name }}</h3>
        <p class="report-subtitle">{{ TULEVA_SYMBOL }} · Monthly report</p>
      </div>
      <close-button popovertarget="fund-report" popovertargetaction="hide" />
    </header>
    <div class="report-picker">
      <label class="report-label" for="fund-report-month">Report</label>
      <select
        id="fund-report-month"
        v-model.number="reportIndex"
        class="form-select form-select-sm report-select"
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
    <div class="fund-stack" aria-hidden="true">
      <span
        v-for="slice in slices"
        :key="slice.label"
        :style="{ flexGrow: slice.value, backgroundColor: slice.color }"
      ></span>
    </div>
    <table class="fund-table">
      <thead>
        <tr>
          <th scope="col">Fund</th>
          <th scope="col" class="num">% of fund</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.name" :class="row.status">
          <td>
            <div class="fund-cell">
              <span
                class="fund-swatch"
                :style="{ backgroundColor: row.color }"
                aria-hidden="true"
              ></span>
              <span>
                <span class="fund-name">{{ row.name }}</span>
                <span v-if="row.status !== 'held'" class="fund-status">{{ row.status }}</span>
              </span>
            </div>
          </td>
          <td class="num">{{ row.weight === null ? '—' : formatPercentage(row.weight) }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { fundReportService } from '../../services/api'
import {
  buildFundChartData,
  CASH,
  cashWeight,
  compareFunds,
  formatReportDate,
  TULEVA_SYMBOL,
  type FundRow,
} from '../../services/fund-allocation'
import type { FundReportDto } from '../../models/generated/domain-models'
import { formatPercentage } from '../../utils/formatters'
import CloseButton from '../shared/close-button.vue'

const props = defineProps<{
  name: string
  reports: FundReportDto[]
}>()

const reportIndex = ref(0)

const report = computed(() => props.reports[reportIndex.value])

const slices = computed(() => buildFundChartData(report.value))

const rows = computed(() => {
  const colors = new Map(slices.value.map(slice => [slice.label, slice.color]))
  const funds = compareFunds(report.value, props.reports[reportIndex.value + 1])
  const cash: FundRow = { isin: '', name: CASH, weight: cashWeight(report.value), status: 'held' }
  return [
    ...funds.filter(fund => fund.status !== 'dropped'),
    cash,
    ...funds.filter(fund => fund.status === 'dropped'),
  ].map(row => ({ ...row, color: colors.get(row.name) }))
})
</script>

<style scoped>
.report-btn {
  display: inline-flex;
  align-items: center;
  padding: 0 0.5rem;
  color: var(--color-surface);
  cursor: pointer;
  background: var(--color-brass);
  border: 1px solid var(--color-brass);
  border-radius: 0 var(--radius-container) var(--radius-container) 0;
  transition: all var(--transition-chip);
}

.report-btn:hover {
  background: var(--color-brass-deep);
  border-color: var(--color-brass-deep);
}

.report-popover {
  width: min(22rem, calc(100% - 2rem));
  margin: auto;
  padding: 1rem;
  color: var(--color-ink);
  background: var(--color-surface);
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-container);
  box-shadow: var(--shadow-overlay);
}

.report-popover:popover-open {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

@supports (anchor-name: --fund-report) {
  .report-popover {
    position-anchor: --fund-report;
    inset: auto;
    top: calc(anchor(bottom) + 0.375rem);
    left: clamp(1rem, anchor(left), calc(100% - 23rem));
    margin: 0;
  }
}

.report-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.report-title {
  margin: 0;
  font-size: 0.9375rem;
  line-height: 1.3;
}

.report-subtitle {
  margin: 0.125rem 0 0;
  font-size: 0.8125rem;
  color: var(--color-ink-soft);
}

.report-picker {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
}

.report-label,
.fund-table th {
  font-size: var(--text-label);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-ink-soft);
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

.fund-stack {
  display: flex;
  gap: 2px;
  height: 0.5rem;
  overflow: hidden;
  border-radius: 2px;
}

.fund-stack span {
  min-width: 2px;
}

.fund-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.fund-table th {
  padding: 0 0 0.375rem;
  text-align: left;
}

.fund-table td {
  padding: 0.4375rem 0;
  border-top: 1px solid var(--color-hairline);
}

.fund-table td.num {
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.fund-table .num {
  text-align: right;
  white-space: nowrap;
}

.fund-cell {
  display: flex;
  align-items: center;
  gap: 0.625rem;
}

.fund-swatch {
  flex-shrink: 0;
  width: 12px;
  height: 12px;
  border: 1px solid var(--color-hairline-strong);
  border-radius: 50%;
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
