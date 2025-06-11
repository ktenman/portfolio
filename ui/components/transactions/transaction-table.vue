<template>
  <data-table
    :items="transactions"
    :columns="columns"
    :is-loading="isLoading"
    empty-message="No transactions found. Add a new transaction to get started."
  >
    <template #cell-instrumentId="{ item }">
      {{ getInstrumentSymbol(item.instrumentId) }}
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
import { Instrument } from '../../models/instrument'
import { useFormatters } from '../../composables/use-formatters'

interface Props {
  transactions: PortfolioTransaction[]
  instruments: Instrument[]
  isLoading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
})

defineEmits<{
  edit: [transaction: PortfolioTransaction]
  delete: [id: number]
}>()

const {
  formatDate,
  formatNumber,
  formatTransactionAmount,
  getAmountClass,
  formatProfitLoss,
  getProfitClass,
} = useFormatters()

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

const getInstrumentSymbol = (instrumentId: number | undefined) => {
  if (instrumentId === undefined) return 'Unknown'
  const instrument = props.instruments.find(i => i.id === instrumentId)
  return instrument ? instrument.symbol : 'Unknown'
}

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
