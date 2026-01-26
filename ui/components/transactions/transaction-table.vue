<template>
  <data-table
    :items="enrichedTransactions"
    :columns="columns"
    :is-loading="isLoading"
    empty-message="No transactions found. Add a new transaction to get started."
  >
    <template #mobile-card="{ item }">
      <div class="mobile-transaction-card">
        <div class="transaction-header">
          <div class="transaction-title">
            <h6 class="instrument-name">
              <span class="ticker-landscape">{{ extractBaseSymbol(item.symbol) }}</span>
              <span class="name-default">{{ item.name }}</span>
            </h6>
            <div class="transaction-meta">
              <span v-if="item.platform" class="badge bg-secondary text-white">
                {{ formatPlatformName(item.platform) }}
              </span>
              <span class="transaction-date">
                {{ formatTransactionDate(item.transactionDate) }}
              </span>
            </div>
          </div>
        </div>
        <div class="transaction-metrics">
          <div class="metric-group">
            <span class="metric-value">{{ formatQuantity(item.quantity) }}</span>
            <span class="metric-label">Quantity</span>
            <small
              v-if="
                item.transactionType === 'BUY' &&
                item.remainingQuantity !== undefined &&
                item.remainingQuantity !== item.quantity &&
                item.remainingQuantity > 0
              "
              class="metric-remaining"
            >
              {{ formatQuantity(item.remainingQuantity) }} left
            </small>
          </div>
          <div class="metric-group">
            <span class="metric-value">
              {{ formatCurrencyWithSign(item.price, item.currency) }}
            </span>
            <span class="metric-label">Price</span>
            <small v-if="item.currency && item.currency !== 'EUR'" class="metric-currency">
              {{ item.currency }}
            </small>
          </div>
          <div v-if="item.commission && item.commission > 0" class="metric-group">
            <span class="metric-value">
              {{ formatCurrencyWithSign(item.commission, item.currency) }}
            </span>
            <span class="metric-label">Fee</span>
          </div>
          <div class="metric-group">
            <span
              class="metric-value"
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
            <span class="metric-label">
              {{ item.transactionType === 'SELL' ? 'Realized' : 'Unrealized' }}
            </span>
          </div>
        </div>
        <div class="transaction-footer">
          <span class="amount-value" :class="getAmountClass(item.transactionType)">
            {{
              formatTransactionAmount(
                item.quantity,
                item.price,
                item.transactionType,
                item.currency
              )
            }}
          </span>
        </div>
      </div>
    </template>

    <template #cell-instrumentId="{ item }">
      <div class="instrument-info">
        <div>
          <span class="ticker-landscape">{{ extractBaseSymbol(item.symbol) }}</span>
          <span class="name-default">{{ item.name }}</span>
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
            item.remainingQuantity !== item.quantity &&
            item.remainingQuantity > 0
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
            item.currency,
            false
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
            item.transactionType === 'SELL' ? item.realizedProfit : item.unrealizedProfit,
            false
          )
        }}
      </span>
    </template>
  </data-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DataTable from '../shared/data-table.vue'
import { TransactionResponseDto } from '../../models/generated/domain-models'
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
  transactions: TransactionResponseDto[]
  isLoading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
})

const columns = transactionColumns

const enrichedTransactions = computed(() => {
  return props.transactions
    .map(transaction => ({
      ...transaction,
      instrumentName: transaction.name,
    }))
    .sort((a, b) => {
      const dateA = new Date(a.transactionDate).getTime()
      const dateB = new Date(b.transactionDate).getTime()
      if (dateA !== dateB) return dateB - dateA
      return (b.id || 0) - (a.id || 0)
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

const formatTransactionDate = (date: string | Date): string => {
  const d = new Date(date)
  return d.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

const extractBaseSymbol = (symbol: string): string => {
  return symbol.split(':')[0]
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

.mobile-transaction-card {
  padding: 0.75rem;

  .transaction-header {
    margin-bottom: 0.75rem;

    .transaction-title {
      .instrument-name {
        margin: 0 0 0.25rem 0;
        font-size: 1rem;
        font-weight: 600;
        line-height: 1.2;
        color: var(--bs-gray-900);
      }

      .transaction-meta {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;

        .transaction-date {
          font-size: 0.75rem;
          color: var(--bs-gray-600);
          font-weight: 500;
        }
      }
    }
  }

  .transaction-metrics {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    margin-bottom: 0.75rem;
    padding-bottom: 0.75rem;
    border-bottom: 1px solid var(--bs-gray-200);
    gap: 0.75rem 0.5rem;

    .metric-group {
      text-align: center;
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;

      .metric-value {
        display: block;
        font-size: 0.875rem;
        font-weight: 600;
        color: var(--bs-gray-900);
        margin-bottom: 0.125rem;

        &.text-success {
          color: #22c55e;
        }

        &.text-danger {
          color: #ef4444;
        }
      }

      .metric-label {
        display: block;
        font-size: 0.6875rem;
        color: var(--bs-gray-600);
        text-transform: uppercase;
        letter-spacing: 0.025em;
        margin-bottom: 0.125rem;
      }

      .metric-remaining,
      .metric-currency {
        display: block;
        font-size: 0.625rem;
        color: var(--bs-gray-500);
        font-weight: 500;
        margin-top: 0.125rem;
      }
    }
  }

  .transaction-footer {
    display: flex;
    justify-content: center;
    align-items: center;

    .amount-value {
      font-size: 1.125rem;
      font-weight: 700;
      color: var(--bs-gray-900);

      &.text-success {
        color: #22c55e;
      }

      &.text-danger {
        color: #ef4444;
      }
    }
  }
}

.ticker-landscape {
  display: none;
}

.name-default {
  display: inline;
}

@media (min-width: 576px) and (max-width: 991px) and (orientation: landscape) {
  .ticker-landscape {
    display: inline;
  }

  .name-default {
    display: none;
  }
}

@media (max-width: 992px) {
  .instrument-info > div:first-child {
    word-break: break-word;
  }
}
</style>
