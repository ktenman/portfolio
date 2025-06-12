<template>
  <data-table
    :items="enrichedTransactions"
    :columns="columns"
    :is-loading="isLoading"
    empty-message="No transactions found. Add a new transaction to get started."
  >
    <template #cell-instrumentId="{ item }">
      <div>
        <div>{{ item.symbol }}</div>
        <small class="text-muted">{{ item.instrumentType }}</small>
      </div>
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
import DataTable from '../shared/data-table.vue'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'
import { transactionColumns } from '../../config/table-columns'
import {
  formatProfitLoss,
  formatTransactionAmount,
  getAmountClass,
  getProfitClass,
} from '../../utils/formatters'

interface Props {
  transactions: PortfolioTransaction[]
  instruments: Instrument[]
  isLoading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
  instruments: () => [],
})

defineEmits<{
  edit: [transaction: PortfolioTransaction]
  delete: [id: number]
}>()

const columns = transactionColumns

const enrichedTransactions = computed(() => {
  return props.transactions.map(transaction => {
    const instrument = props.instruments.find(i => i.id === transaction.instrumentId)
    return {
      ...transaction,
      instrumentName: instrument?.name,
      instrumentType: instrument?.type,
    }
  })
})

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
