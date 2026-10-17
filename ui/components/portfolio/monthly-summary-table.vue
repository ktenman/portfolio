<template>
  <data-table
    :items="sortedItems"
    :columns="COLUMNS"
    key-field="month"
    :is-loading="model === undefined"
    :sortable="true"
    :sort-state="sortState"
    :on-sort="toggleSort"
  >
    <template #cell-month="{ item }">
      <span class="month-label">{{ item.label }}</span>
      <span v-if="item.coverage" class="month-coverage">{{ item.coverage }}</span>
      <span v-if="item.note" class="month-note">{{ item.note }}</span>
    </template>
    <template #cell-monthChange="{ item }">
      <span :class="getGainLossClass(item.monthChange)">
        {{ formatCurrencyWithSymbol(item.monthChange) }}
      </span>
      <template v-if="item.monthPercent === null">
        <span class="change-percentage">(—)</span>
        <span class="sr-only">percentage unavailable</span>
      </template>
      <span v-else class="change-percentage" :class="percentClass(item.monthPercent)">
        ({{ formatSignedPercent(Number(item.monthPercent.toFixed(1)) || 0, 1) }})
      </span>
    </template>
  </data-table>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useSortableTable } from '../../composables/use-sortable-table'
import { summaryValueColumns, totalValueColumn } from '../../config/table-columns'
import { percentClass, type MonthlyProfit } from '../../services/monthly-profit'
import {
  formatCurrencyWithSymbol,
  formatSignedPercent,
  getGainLossClass,
} from '../../utils/formatters'
import DataTable, { type ColumnDefinition } from '../shared/data-table.vue'

const props = defineProps<{ model: MonthlyProfit | undefined }>()

const COLUMNS: ColumnDefinition[] = [
  { key: 'month', label: 'Month' },
  ...summaryValueColumns,
  { key: 'monthChange', label: 'Month Change', class: 'text-right!' },
  totalValueColumn,
]

const items = computed(() =>
  (props.model?.months ?? []).map(month => ({
    ...month.row,
    month: month.key,
    monthChange: month.change,
    monthPercent: month.percent,
    label: month.label,
    coverage: month.coverage,
    note: month.note,
  }))
)

const { sortedItems, sortState, toggleSort } = useSortableTable(items, 'month', 'desc')
</script>

<style scoped>
.month-coverage {
  margin-left: 0.375rem;
  color: var(--color-ink-soft);
}

.month-note {
  display: block;
  font-size: var(--text-xs);
  color: var(--color-notice);
}

.change-percentage {
  margin-left: 0.25rem;
  font-size: 0.85em;
}
</style>
