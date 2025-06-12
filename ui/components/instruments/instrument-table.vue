<template>
  <data-table
    :items="instruments"
    :columns="columns"
    :is-loading="isLoading"
    empty-message="No instruments found. Add a new instrument to get started."
  >
    <template #cell-profit="{ item }">
      <span :class="amountClass(item)">
        {{ formattedAmount(item) }}
      </span>
    </template>

    <template #actions="{ item }">
      <button class="btn btn-sm btn-secondary" @click="$emit('edit', item)">
        <font-awesome-icon icon="pencil-alt" />
        <span class="d-none d-md-inline ms-1">Edit</span>
      </button>
    </template>
  </data-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DataTable, { ColumnDefinition } from '../shared/data-table.vue'
import { Instrument } from '../../models/instrument'
import {
  formatCurrency,
  formatNumber,
  formatPercentage,
  formatProfitLoss,
  getProfitClass,
} from '../../utils/formatters'

interface Props {
  instruments: Instrument[]
  isLoading?: boolean
}

withDefaults(defineProps<Props>(), {
  isLoading: false,
})

defineEmits<{
  edit: [instrument: Instrument]
}>()

const columns = computed<ColumnDefinition[]>(() => [
  {
    key: 'symbol',
    label: 'Symbol',
  },
  {
    key: 'name',
    label: 'Name',
  },
  {
    key: 'baseCurrency',
    label: 'Currency',
  },
  {
    key: 'quantity',
    label: 'Quantity',
    class: 'd-none d-md-table-cell',
    formatter: (value: number) => formatNumber(value),
  },
  {
    key: 'currentPrice',
    label: 'Current Price',
    class: 'd-none d-md-table-cell',
    formatter: (value: number) => formatCurrency(value),
  },
  {
    key: 'xirr',
    label: 'XIRR Annual Return',
    formatter: (value: number) => formatPercentage(value),
  },
  {
    key: 'totalInvestment',
    label: 'Invested',
    formatter: (value: number) => formatCurrency(value),
  },
  {
    key: 'currentValue',
    label: 'Current Value',
    formatter: (value: number) => formatCurrency(value),
  },
  {
    key: 'profit',
    label: 'Profit/Loss',
  },
])

const formattedAmount = (instrument: Instrument): string => {
  return formatProfitLoss(instrument.profit)
}

const amountClass = (instrument: Instrument): string => {
  return getProfitClass(instrument.profit)
}
</script>
