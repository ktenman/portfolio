<template>
  <data-table
    :items="sortedItems"
    :columns="columns"
    :row-class="rowClass"
    :sortable="true"
    :sort-state="sortState"
    :on-sort="toggleSort"
    :key-field="interval === 'monthly' ? 'monthKey' : 'date'"
  >
    <template v-if="interval === 'monthly'" #cell-monthKey="{ item }">
      <span>{{ formatMonth(item.monthKey ?? '') }}</span>
      <span v-if="item.coverage?.length" class="month-coverage">
        {{ item.coverage.join(' · ') }}
      </span>
    </template>
    <template v-if="interval === 'monthly'" #cell-monthChange="{ item }">
      <span class="month-change-amount" :class="getGainLossClass(item.monthChange)">
        {{ Number.isFinite(item.monthChange) ? formatCurrencyChange(item.monthChange ?? 0) : '—' }}
      </span>
      <span
        class="change-percentage month-change-percent"
        :class="getGainLossClass(item.monthPercent)"
        :aria-label="item.monthPercent === null ? 'Percentage unavailable' : undefined"
      >
        ({{ formatMonthlyPercent(item.monthPercent ?? null) }})
      </span>
    </template>
    <template v-if="interval === 'daily'" #cell-totalProfitChange24h="{ value, item }">
      <span v-if="value && Math.abs(value) > 0.01" :class="getGainLossClass(value)">
        {{ formatDailyChange(item.totalProfitChange24h) }}
        <span class="change-percentage">{{ formatDailyPercent(item) }}</span>
      </span>
    </template>
  </data-table>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'
import { useSortableTable } from '../../composables/use-sortable-table'
import type { SummaryInterval } from '../../composables/use-summary-interval'
import { formatDateToString } from '../../composables/use-quick-dates'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import { formatMonth, formatMonthlyPercent, type ProfitMonth } from '../../services/monthly-profit'
import { percentOfPreviousValue } from '../../services/summary-aggregator'
import {
  formatCurrencyChange,
  formatCurrencyWithSymbol,
  formatDate,
  formatPercentageFromDecimal,
  formatSignedPercent,
  getGainLossClass,
} from '../../utils/formatters'

const props = defineProps<{
  rows: PortfolioSummaryDto[]
  months?: ProfitMonth[]
  interval: SummaryInterval
}>()

interface SummaryTableRow extends PortfolioSummaryDto {
  monthKey?: string
  monthChange?: number
  monthPercent?: number | null
  coverage?: string[]
}

const items = computed<SummaryTableRow[]>(() =>
  props.interval === 'daily'
    ? props.rows
    : (props.months ?? []).map(month => ({
        ...month.closing,
        monthKey: month.key,
        monthChange: month.change,
        monthPercent: month.percent,
        coverage: month.coverage,
      }))
)

const { sortedItems, sortState, toggleSort } = useSortableTable(
  items,
  props.interval === 'monthly' ? 'monthKey' : 'date',
  'desc'
)

const formatDailyChange = (value: number | null) =>
  value === null || Math.abs(value) <= 0.01 ? '' : formatCurrencyWithSymbol(value)

const formatDailyPercent = (summary: PortfolioSummaryDto) => {
  const change = summary.totalProfitChange24h
  if (change === null || Math.abs(change) <= 0.01) return ''
  const percent = percentOfPreviousValue(summary.totalValue, change)
  return percent === null ? '' : `(${formatSignedPercent(percent)})`
}

const numericColumn = (key: string, label: string, hideOnMobile = false): ColumnDefinition => ({
  key,
  label,
  formatter: key === 'xirrAnnualReturn' ? formatPercentageFromDecimal : formatCurrencyWithSymbol,
  class: hideOnMobile ? 'hidden! md:table-cell! text-right!' : 'text-right!',
  hideOnMobile,
})

const columns = computed<ColumnDefinition[]>(() => [
  props.interval === 'daily'
    ? { key: 'date', label: 'Date', formatter: formatDate }
    : { key: 'monthKey', label: 'Month' },
  numericColumn('xirrAnnualReturn', 'XIRR Annual Return'),
  numericColumn('earningsPerDay', 'Earnings Per Day', true),
  numericColumn('earningsPerMonth', 'Earnings Per Month'),
  numericColumn('unrealizedProfit', 'Unrealized Profit', true),
  numericColumn('totalProfit', 'Total Profit'),
  props.interval === 'daily'
    ? {
        key: 'totalProfitChange24h',
        label: '24h Change',
        formatter: formatDailyChange,
        class: 'text-right!',
      }
    : { key: 'monthChange', label: 'Month Change', class: 'text-right!' },
  numericColumn('totalValue', 'Total Value'),
])

const rowClass = (summary: SummaryTableRow, index: number) => ({
  'font-weight-bold': index === 0 && summary.date === formatDateToString(new Date()),
})
</script>

<style scoped>
.change-percentage {
  margin-left: 0.25rem;
  font-size: 0.85em;
}

.month-coverage {
  display: block;
  max-width: 28ch;
  margin-top: 0.25rem;
  font-size: var(--text-label);
  font-weight: 400;
  white-space: normal;
  color: var(--color-ink-soft);
}
</style>
