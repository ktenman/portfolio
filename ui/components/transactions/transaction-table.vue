<template>
  <data-table
    :items="enrichedTransactions"
    :columns="columns"
    :is-loading="isLoading"
    empty-message="No transactions found. Add a new transaction to get started."
  >
    <template #cell-instrumentId="{ item }">
      <div class="instrument-info">
        <div class="small text-truncate">{{ item.instrumentName }}</div>
        <small class="text-muted">{{ item.symbol }}</small>
      </div>
    </template>

    <template #cell-amount="{ item }">
      <span :class="getAmountClass(item.transactionType)">
        {{ formatTransactionAmount(item.quantity, item.price, item.transactionType) }}
      </span>
    </template>

    <template #cell-profit="{ item }">
      <span
        :class="
          getProfitClass(
            item.transactionType === 'SELL' ? item.realizedProfit : item.unrealizedProfit
          )
        "
      >
        {{
          formatProfitLoss(
            item.transactionType === 'SELL' ? item.realizedProfit : item.unrealizedProfit
          )
        }}
      </span>
    </template>

    <template #actions="{ item }">
      <button class="btn btn-sm btn-secondary me-2" @click="$emit('edit', item)">
        <base-icon name="pencil" :size="14" />
        <span class="d-none d-lg-inline ms-1">Edit</span>
      </button>
      <button
        class="btn btn-sm btn-danger hide-on-mobile"
        @click="item.id && $emit('delete', item.id)"
      >
        <base-icon name="trash" :size="14" />
        <span class="d-none d-lg-inline ms-1">Delete</span>
      </button>
    </template>
  </data-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DataTable from '../shared/data-table.vue'
import BaseIcon from '../shared/base-icon.vue'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'
import { transactionColumns } from '../../config'
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

const instrumentMap = computed(() => {
  if (!props.instruments?.length) return new Map<number, Instrument>()

  return new Map(props.instruments.map(instrument => [instrument.id!, instrument]))
})

const enrichedTransactions = computed(() => {
  const instMap = instrumentMap.value

  return props.transactions.map(transaction => {
    const instrument = instMap.get(transaction.instrumentId)

    return {
      ...transaction,
      instrumentName: instrument?.name || 'Unknown',
      symbol: instrument?.symbol || '-',
    }
  })
})
</script>

<style scoped lang="scss">
@import '../../styles/shared-table.scss';

@media (max-width: 992px) {
  .instrument-info > div:first-child {
    max-width: 20ch;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 794px) {
  .hide-on-mobile {
    display: none !important;
  }
}
</style>
