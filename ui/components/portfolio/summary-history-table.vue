<template>
  <data-table
    v-if="months"
    :items="sortedMonths"
    :columns="MONTH_COLUMNS"
    :sortable="true"
    :sort-state="monthSort"
    :on-sort="toggleMonthSort"
  >
    <template #cell-month="{ item }">
      <span class="month-name">
        <span>{{ formatMonthYear(item.month) }}</span>
        <span v-if="item.coverage" class="month-coverage">{{ ` ${item.coverage}` }}</span>
        <span v-if="item.notes.length" class="month-notes">{{ ` ${item.notes.join('. ')}` }}</span>
      </span>
    </template>
    <template #cell-monthChange="{ item }">
      <span class="month-change">
        <span :class="getGainLossClass(item.monthChange)">
          {{ formatCurrencyWithSymbol(item.monthChange) }}
        </span>
        <span class="change-percentage" :class="getGainLossClass(item.monthPercent)">
          ({{ item.monthPercent === null ? '—' : formatSignedPercent(item.monthPercent, 1) }})
        </span>
      </span>
    </template>
  </data-table>
  <data-table
    v-else
    :items="sortedDays"
    :columns="DAY_COLUMNS"
    :row-class="dayRowClass"
    :sortable="true"
    :sort-state="daySort"
    :on-sort="toggleDaySort"
  >
    <template #cell-totalProfitChange24h="{ value, item }">
      <span v-if="value && Math.abs(value) > 0.01" :class="getGainLossClass(value)">
        {{ format24hChange(item.totalProfitChange24h) }}
        <span class="change-percentage">{{ format24hChangePercentage(item) }}</span>
      </span>
    </template>
  </data-table>
</template>

<script lang="ts" setup>
import { computed, toRef } from 'vue'
import { formatDateToString } from '../../composables/use-quick-dates'
import { useSortableTable } from '../../composables/use-sortable-table'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import type { MonthResult } from '../../services/monthly-profit'
import { percentOfPreviousValue } from '../../services/summary-aggregator'
import {
  formatCurrencyWithSymbol,
  formatDate,
  formatPercentageFromDecimal,
  formatSignedPercent,
  getGainLossClass,
} from '../../utils/formatters'
import { formatMonthYear } from '../../utils/iso-dates'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'

const props = defineProps<{ days: PortfolioSummaryDto[]; months: MonthResult[] | null }>()

const HIDDEN_ON_MOBILE = 'hidden! md:table-cell! text-right!'

const format24hChange = (value: number | null) => {
  if (value === null || value === 0 || Math.abs(value) <= 0.01) {
    return ''
  }
  return formatCurrencyWithSymbol(value)
}

const format24hChangePercentage = (summary: PortfolioSummaryDto) => {
  const change = summary.totalProfitChange24h
  if (change === null || Math.abs(change) <= 0.01) {
    return ''
  }
  const percent = percentOfPreviousValue(summary.totalValue, change)
  if (percent === null) {
    return ''
  }
  return `(${formatSignedPercent(percent)})`
}

const columnsOf = (first: ColumnDefinition, change: ColumnDefinition): ColumnDefinition[] => [
  first,
  {
    key: 'xirrAnnualReturn',
    label: 'XIRR Annual Return',
    formatter: formatPercentageFromDecimal,
    class: 'text-right!',
  },
  {
    key: 'earningsPerDay',
    label: 'Earnings Per Day',
    formatter: formatCurrencyWithSymbol,
    class: HIDDEN_ON_MOBILE,
    hideOnMobile: true,
  },
  {
    key: 'earningsPerMonth',
    label: 'Earnings Per Month',
    formatter: formatCurrencyWithSymbol,
    class: 'text-right!',
  },
  {
    key: 'unrealizedProfit',
    label: 'Unrealized Profit',
    formatter: formatCurrencyWithSymbol,
    class: HIDDEN_ON_MOBILE,
    hideOnMobile: true,
  },
  {
    key: 'totalProfit',
    label: 'Total Profit',
    formatter: formatCurrencyWithSymbol,
    class: 'text-right!',
  },
  { ...change, class: 'text-right!' },
  {
    key: 'totalValue',
    label: 'Total Value',
    formatter: formatCurrencyWithSymbol,
    class: 'text-right!',
  },
]

const DAY_COLUMNS = columnsOf(
  { key: 'date', label: 'Date', formatter: formatDate },
  { key: 'totalProfitChange24h', label: '24h Change', formatter: format24hChange }
)

const MONTH_COLUMNS = columnsOf(
  { key: 'month', label: 'Month' },
  { key: 'monthChange', label: 'Month Change' }
)

const monthRows = computed(() =>
  (props.months ?? []).map(({ key, coverage, notes, change, percent, closing }) => ({
    month: key,
    coverage,
    notes,
    xirrAnnualReturn: closing.xirrAnnualReturn,
    earningsPerDay: closing.earningsPerDay,
    earningsPerMonth: closing.earningsPerMonth,
    unrealizedProfit: closing.unrealizedProfit,
    totalProfit: closing.totalProfit,
    monthChange: change,
    monthPercent: percent,
    totalValue: closing.totalValue,
  }))
)

const {
  sortedItems: sortedDays,
  sortState: daySort,
  toggleSort: toggleDaySort,
} = useSortableTable(toRef(props, 'days'), 'date', 'desc')

const {
  sortedItems: sortedMonths,
  sortState: monthSort,
  toggleSort: toggleMonthSort,
} = useSortableTable(monthRows, 'month', 'desc')

const dayRowClass = (summary: PortfolioSummaryDto, index: number) => ({
  'font-weight-bold': index === 0 && summary.date === formatDateToString(new Date()),
})
</script>

<style scoped>
.change-percentage {
  margin-left: 0.25rem;
  font-size: 0.85em;
}

.month-coverage,
.month-notes {
  font-size: 0.85em;
  color: var(--color-ink-soft);
}

.month-notes {
  display: block;
  color: var(--color-notice);
}
</style>
