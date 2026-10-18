<template>
  <data-table
    :items="sortedItems"
    :columns="COLUMNS"
    :row-class="rowClass"
    :sortable="true"
    :sort-state="sortState"
    :on-sort="toggleSort"
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
import { toRef } from 'vue'
import { formatDateToString } from '../../composables/use-quick-dates'
import { useSortableTable } from '../../composables/use-sortable-table'
import { summaryValueColumns, totalValueColumn } from '../../config/table-columns'
import type { PortfolioSummaryDto } from '../../models/generated/domain-models'
import { percentOfPreviousValue } from '../../services/summary-aggregator'
import {
  formatCurrencyWithSymbol,
  formatDate,
  formatSignedPercent,
  getGainLossClass,
} from '../../utils/formatters'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'

const props = defineProps<{ summaries: PortfolioSummaryDto[] }>()

const { sortedItems, sortState, toggleSort } = useSortableTable(
  toRef(() => props.summaries),
  'date',
  'desc'
)

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

const COLUMNS: ColumnDefinition[] = [
  { key: 'date', label: 'Date', formatter: formatDate },
  ...summaryValueColumns,
  {
    key: 'totalProfitChange24h',
    label: '24h Change',
    formatter: format24hChange,
    class: 'text-right!',
  },
  totalValueColumn,
]

const rowClass = (summary: PortfolioSummaryDto, index: number) => ({
  'font-weight-bold': index === 0 && summary.date === formatDateToString(new Date()),
})
</script>

<style scoped>
.change-percentage {
  margin-left: 0.25rem;
  font-size: 0.85em;
}

@media (max-width: 575px) {
  :deep(.table) {
    font-size: 12px;
  }

  :deep(.hide-on-mobile) {
    display: none;
  }
}
</style>
