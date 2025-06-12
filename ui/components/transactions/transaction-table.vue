<template>
  <data-table
    :items="transactions"
    :columns="columns"
    :is-loading="isLoading"
    empty-message="No transactions found. Add a new transaction to get started."
  >
    <template #cell-instrumentId="{ item }">
      {{ item.symbol }}
    </template>

    <template #cell-amount="{ item }">
      <span :class="amountClass(item)">{{ formattedAmount(item) }}</span>
    </template>

    <template #cell-profit="{ item }">
      <span :class="getProfitClass(getTransactionProfit(item))">
        {{ formatProfitLoss(getTransactionProfit(item)) }}
      </span>
    </template>

    <template #actions="{ item }">
      <button class="btn btn-sm btn-secondary me-2" @click="$emit('edit', item)">
        <font-awesome-icon icon="pencil-alt" />
        <span class="d-none d-md-inline ms-1">Edit</span>
      </button>
      <button
        class="btn btn-sm btn-danger d-none d-md-inline-block"
        @click="item.id && $emit('delete', item.id)"
      >
        <font-awesome-icon icon="trash-alt" />
        <span class="ms-1">Delete</span>
      </button>
    </template>
  </data-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DataTable, { ColumnDefinition } from '../shared/data-table.vue'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import {
  formatDate,
  formatNumber,
  formatProfitLoss,
  formatTransactionAmount,
  getAmountClass,
  getProfitClass,
} from '../../utils/formatters'

interface Props {
  transactions: PortfolioTransaction[]
  isLoading?: boolean
}

withDefaults(defineProps<Props>(), {
  isLoading: false,
})

defineEmits<{
  edit: [transaction: PortfolioTransaction]
  delete: [id: number]
}>()

const columns = computed<ColumnDefinition[]>(() => [
  {
    key: 'transactionDate',
    label: 'Date',
    formatter: (value: string) => formatDate(value),
  },
  {
    key: 'instrumentId',
    label: 'Instrument',
  },
  {
    key: 'quantity',
    label: 'Quantity',
    class: 'd-none d-md-table-cell',
    formatter: (value: number) => formatNumber(value),
  },
  {
    key: 'price',
    label: 'Price',
    class: 'd-none d-md-table-cell',
    formatter: (value: number) => formatNumber(value),
  },
  {
    key: 'amount',
    label: 'Amount',
  },
  {
    key: 'profit',
    label: 'Profit/Loss',
  },
  {
    key: 'averageCost',
    label: 'Average Cost',
    class: 'd-none d-md-table-cell',
    formatter: (value: number) => formatNumber(value),
  },
])

const formattedAmount = (transaction: PortfolioTransaction): string => {
  return formatTransactionAmount(
    transaction.quantity,
    transaction.price,
    transaction.transactionType
  )
}

const amountClass = (transaction: PortfolioTransaction): string => {
  return getAmountClass(transaction.transactionType)
}

const getTransactionProfit = (transaction: PortfolioTransaction): number | null | undefined => {
  return transaction.transactionType === 'SELL'
    ? transaction.realizedProfit
    : transaction.unrealizedProfit
}
</script>
