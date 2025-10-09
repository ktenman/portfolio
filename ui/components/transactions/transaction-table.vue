<template>
  <data-table
    :items="enrichedTransactions"
    :columns="columns"
    :is-loading="isLoading"
    empty-message="No transactions found. Add a new transaction to get started."
  >
    <template #cell-instrumentId="{ item }">
      <div class="instrument-info">
        <div>
          <span class="d-block">{{ item.instrumentName }}</span>
        </div>
        <div v-if="item.platform" class="platform-tags mt-1">
          <span class="badge bg-secondary text-white">
            {{ formatPlatformName(item.platform) }}
          </span>
        </div>
      </div>
    </template>

    <template #cell-quantityInfo="{ item }">
      <span>
        <span class="d-block">{{ formatQuantity(item.quantity) }}</span>
        <small
          v-if="
            item.transactionType === 'BUY' &&
            item.remainingQuantity !== undefined &&
            item.remainingQuantity !== item.quantity
          "
          class="d-block text-muted"
        >
          Remaining: {{ formatQuantity(item.remainingQuantity) }}
        </small>
      </span>
    </template>

    <template #cell-price="{ item }">
      <span>
        <span class="d-block">{{ formatCurrencyWithSign(item.price, item.currency) }}</span>
        <small v-if="item.currency && item.currency !== 'EUR'" class="d-block text-muted">
          {{ item.currency }}
        </small>
      </span>
    </template>

    <template #cell-amount="{ item }">
      <span :class="getAmountClass(item.transactionType)">
        {{
          formatTransactionAmount(
            item.quantity,
            item.price,
            item.transactionType,
            item.commission,
            item.currency
          )
        }}
        <small v-if="item.commission && item.commission > 0" class="d-block text-muted">
          Fee: {{ formatCurrencyWithSign(item.commission, item.currency) }}
        </small>
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

    <!-- <template #actions="{ item }">
      <div class="action-buttons">
        <button
          class="btn btn-sm btn-ghost btn-secondary btn-table-action"
          @click="$emit('edit', item)"
          title="Edit"
        >
          <base-icon name="pencil" :size="14" />
          <span class="ms-1 d-inline d-lg-none">Edit</span>
        </button>
        <button
          class="btn btn-sm btn-ghost btn-danger btn-table-action"
          @click="item.id && $emit('delete', item.id)"
          title="Delete"
        >
          <base-icon name="trash" :size="14" />
          <span class="ms-1 d-inline d-lg-none">Delete</span>
        </button>
      </div>
    </template> -->
  </data-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DataTable from '../shared/data-table.vue'
import { PortfolioTransaction } from '../../models/generated/domain-models'
import { Instrument } from '../../models/generated/domain-models'
import { transactionColumns } from '../../config'
import {
  formatProfitLoss,
  formatTransactionAmount,
  formatQuantity,
  getAmountClass,
  getProfitClass,
  formatCurrencyWithSign,
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
    }
  })
})

const formatPlatformName = (platform: string): string => {
  const platformMap: Record<string, string> = {
    TRADING212: 'Trading 212',
    LIGHTYEAR: 'Lightyear',
    SWEDBANK: 'Swedbank',
    BINANCE: 'Binance',
    COINBASE: 'Coinbase',
    LHV: 'LHV',
    AVIVA: 'Aviva',
    UNKNOWN: 'Unknown',
  }

  return platformMap[platform] || platform
}
</script>

<style scoped lang="scss">
@import '../../styles/shared-table.scss';

.platform-tags {
  .badge {
    color: white !important;
    background-color: #6b7280 !important;
    font-weight: 500;
  }
}

@media (max-width: 992px) {
  .instrument-info > div:first-child {
    word-break: break-word;
  }
}
</style>
