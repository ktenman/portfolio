<template>
  <data-table
    :items="instruments"
    :columns="columns"
    :is-loading="isLoading"
    :is-error="isError"
    :error-message="errorMessage"
    empty-message="No instruments found. Add a new instrument to get started."
  >
    <template #footer>
      <tr v-if="instruments.length > 0" class="table-footer-totals d-none d-md-table-row">
        <td class="fw-bold">Total</td>
        <td></td>
        <td></td>
        <td class="fw-bold text-nowrap">{{ formatCurrencyWithSign(totalValue, 'EUR') }}</td>
        <td class="fw-bold text-nowrap">{{ formatCurrencyWithSign(totalInvested, 'EUR') }}</td>
        <td class="fw-bold text-nowrap">
          <span :class="getProfitClass(totalProfit)">
            {{ formatProfit(totalProfit, 'EUR') }}
          </span>
        </td>
        <td class="fw-bold text-nowrap">
          <span :class="getProfitClass(totalChangeAmount)">
            {{ formatCurrencyWithSign(Math.abs(totalChangeAmount), 'EUR') }} /
            {{ Math.abs(totalChangePercent).toFixed(2) }}%
          </span>
        </td>
        <td class="fw-bold text-nowrap">{{ formatPercentageFromDecimal(totalXirr) }}</td>
      </tr>
    </template>
    <template #mobile-card="{ item }">
      <div class="mobile-instrument-card">
        <div class="instrument-header">
          <div class="instrument-title">
            <h6 class="instrument-name">{{ item.name }}</h6>
            <span class="instrument-symbol">{{ item.symbol }}</span>
            <div v-if="item.platforms && item.platforms.length > 0" class="platform-tags mt-1">
              <span
                v-for="platform in item.platforms"
                :key="platform"
                class="badge bg-secondary me-1 text-white"
              >
                {{ formatPlatformName(platform) }}
              </span>
            </div>
          </div>
          <!-- <button
            class="btn btn-sm btn-ghost btn-secondary btn-table-action"
            @click="$emit('edit', item)"
            title="Edit"
          >
            <base-icon name="pencil" :size="16" />
          </button> -->
        </div>
        <div class="instrument-metrics">
          <div class="metric-group">
            <span class="metric-value">{{ formatQuantity(item.quantity) }}</span>
            <span class="metric-label">Quantity</span>
          </div>
          <div class="metric-group">
            <span class="metric-value">
              {{ formatCurrencyWithSign(item.currentPrice, item.baseCurrency) }}
            </span>
            <span class="metric-label">Price</span>
          </div>
          <div class="metric-group">
            <span class="metric-value">{{ formatPercentageFromDecimal(item.xirr) }}</span>
            <span class="metric-label">XIRR</span>
          </div>
          <div
            v-if="item.priceChangeAmount !== undefined && item.priceChangeAmount !== null"
            class="metric-group"
          >
            <span class="metric-value" :class="getProfitClass(item.priceChangeAmount)">
              {{ formatCurrencyWithSign(Math.abs(item.priceChangeAmount), item.baseCurrency) }}
            </span>
            <span class="metric-label">24H</span>
          </div>
        </div>
        <div class="instrument-footer">
          <div class="value-info">
            <span class="value-label">Value</span>
            <span class="value-amount">
              {{ formatCurrencyWithSign(item.currentValue, item.baseCurrency) }}
            </span>
          </div>
          <div class="profit-info">
            <span :class="getProfitClass(getItemProfit(item))">
              {{ formatProfit(getItemProfit(item), item.baseCurrency) }}
            </span>
          </div>
        </div>
      </div>
    </template>

    <template #mobile-footer>
      <div v-if="instruments.length > 0" class="mobile-totals-card">
        <div class="totals-header">
          <h6 class="totals-title">TOTAL</h6>
        </div>
        <hr class="totals-divider" />
        <div class="totals-content">
          <div class="total-item">
            <span class="total-label">VALUE</span>
            <span class="total-value">{{ formatCurrencyWithSign(totalValue, 'EUR') }}</span>
          </div>
          <div class="total-item">
            <span class="total-label">INVESTED</span>
            <span class="total-value">{{ formatCurrencyWithSign(totalInvested, 'EUR') }}</span>
          </div>
          <div class="total-item">
            <span class="total-label">PROFIT</span>
            <span class="total-value" :class="getProfitClass(totalProfit)">
              {{ formatProfit(totalProfit, 'EUR') }}
            </span>
          </div>
          <div class="total-item">
            <span class="total-label">24H</span>
            <span class="total-value" :class="getProfitClass(totalChangeAmount)">
              {{ formatCurrencyWithSign(Math.abs(totalChangeAmount), 'EUR') }} /
              {{ Math.abs(totalChangePercent).toFixed(2) }}%
            </span>
          </div>
          <div class="total-item">
            <span class="total-label">XIRR</span>
            <span class="total-value">{{ formatPercentageFromDecimal(totalXirr) }}</span>
          </div>
        </div>
      </div>
    </template>

    <template #cell-instrument="{ item }">
      <div class="instrument-info">
        <div>
          <span class="d-block instrument-name">{{ item.name }}</span>
          <small class="d-block text-muted instrument-symbol">{{ item.symbol }}</small>
        </div>
        <div v-if="item.platforms && item.platforms.length > 0" class="platform-tags mt-1">
          <span
            v-for="platform in item.platforms"
            :key="platform"
            class="badge bg-secondary me-1 text-white"
          >
            {{ formatPlatformName(platform) }}
          </span>
        </div>
      </div>
    </template>

    <template #cell-type="{ item }">
      {{ item.type || item.category || '-' }}
    </template>

    <template #cell-currentPrice="{ item }">
      {{ formatCurrencyWithSign(item.currentPrice, item.baseCurrency) }}
    </template>

    <template #cell-priceChange="{ item }">
      <span v-html="formatPriceChange(item)"></span>
    </template>

    <template #cell-totalInvestment="{ item }">
      {{ formatCurrencyWithSign(item.totalInvestment, item.baseCurrency) }}
    </template>

    <template #cell-currentValue="{ item }">
      {{ formatCurrencyWithSign(item.currentValue, item.baseCurrency) }}
    </template>

    <template #cell-profit="{ item }">
      <span :class="getProfitClass(getItemProfit(item))" class="profit-display text-nowrap">
        {{ formatProfit(getItemProfit(item), item.baseCurrency) }}
      </span>
    </template>

    <!-- <template #actions="{ item }">
      <div class="action-buttons">
        <button
          class="btn btn-sm btn-ghost btn-secondary btn-table-action"
          @click="$emit('edit', item)"
          title="Edit"
        >
          Edit
        </button>
      </div>
    </template> -->
  </data-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DataTable from '../shared/data-table.vue'
import { Instrument } from '../../models/instrument'
import { instrumentColumns } from '../../config'
import {
  getProfitClass,
  formatCurrencyWithSign,
  formatQuantity,
  formatPercentageFromDecimal,
  formatPriceChange,
} from '../../utils/formatters'

interface Props {
  instruments: Instrument[]
  isLoading?: boolean
  isError?: boolean
  errorMessage?: string
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
  isError: false,
})

defineEmits<{
  edit: [instrument: Instrument]
}>()

const columns = instrumentColumns

const totalInvested = computed(() => {
  return props.instruments.reduce((sum, instrument) => {
    return sum + (instrument.totalInvestment || 0)
  }, 0)
})

const totalValue = computed(() => {
  return props.instruments.reduce((sum, instrument) => {
    return sum + (instrument.currentValue || 0)
  }, 0)
})

const totalProfit = computed(() => {
  return props.instruments.reduce((sum, instrument) => {
    return sum + getItemProfit(instrument)
  }, 0)
})

const totalXirr = computed(() => {
  const weightedSum = props.instruments.reduce((sum, instrument) => {
    const xirr = instrument.xirr || 0
    const invested = instrument.totalInvestment || 0
    return sum + xirr * invested
  }, 0)

  return totalInvested.value > 0 ? weightedSum / totalInvested.value : 0
})

const totalChangeAmount = computed(() => {
  return props.instruments.reduce((sum, instrument) => {
    return sum + (instrument.priceChangeAmount || 0)
  }, 0)
})

const totalChangePercent = computed(() => {
  if (totalValue.value === 0) return 0
  return (totalChangeAmount.value / totalValue.value) * 100
})

const getItemProfit = (item: Instrument): number => {
  const value = item.currentValue || 0
  const invested = item.totalInvestment || 0
  return value - invested
}

const formatProfit = (amount: number, currency: string | undefined): string => {
  const sign = amount >= 0 ? '+' : '-'
  return sign + formatCurrencyWithSign(Math.abs(amount), currency || 'EUR')
}

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

.table-footer-totals {
  background-color: var(--bs-gray-100);
  border-top: 2px solid var(--bs-border-color);

  td {
    padding: 1rem 0.75rem;
    font-weight: 600;
    color: var(--bs-gray-700);
    vertical-align: middle;

    &:first-child {
      color: var(--bs-gray-800);
      font-weight: 700;
      text-transform: uppercase;
      font-size: 0.875rem;
      letter-spacing: 0.025em;
    }
  }
}

// Mobile totals card
.mobile-totals-card {
  margin-bottom: 0.75rem;
  padding: 1rem;
  background: white;
  border: 1px solid var(--bs-gray-200);
  border-radius: 0.5rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);

  .totals-header {
    text-align: center;
    margin-bottom: 0.5rem;

    .totals-title {
      margin: 0;
      font-size: 0.875rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      color: var(--bs-gray-800);
    }
  }

  .totals-divider {
    margin: 0.75rem 0;
    border: 0;
    border-top: 1px solid var(--bs-gray-200);
    opacity: 1;
  }

  .totals-content {
    display: flex;
    flex-direction: column;
    gap: 1rem;

    .total-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 0.25rem;

      .total-label {
        font-size: 0.6875rem;
        font-weight: 500;
        color: var(--bs-gray-600);
        text-transform: uppercase;
        letter-spacing: 0.05em;
      }

      .total-value {
        font-size: 1.125rem;
        font-weight: 700;
        color: var(--bs-gray-900);
        line-height: 1.2;

        &.text-success {
          color: #22c55e;
        }

        &.text-danger {
          color: #ef4444;
        }
      }
    }
  }
}

// Mobile card styles
.mobile-instrument-card {
  padding: 0.75rem;

  .instrument-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 0.75rem;

    .instrument-title {
      flex: 1;

      .instrument-name {
        margin: 0;
        font-size: 1rem;
        font-weight: 600;
        line-height: 1.2;
        color: var(--bs-gray-900);
      }

      .instrument-symbol {
        font-size: 0.75rem;
        color: var(--bs-gray-600);
        font-weight: 500;
      }
    }

    .btn-link {
      color: var(--bs-gray-600);
      opacity: 0.7;
      transition: opacity 0.2s;

      &:hover {
        opacity: 1;
      }
    }
  }

  .instrument-metrics {
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
      }
    }
  }

  .instrument-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .value-info {
      .value-label {
        font-size: 0.75rem;
        color: var(--bs-gray-600);
        margin-right: 0.5rem;
      }

      .value-amount {
        font-size: 0.9375rem;
        font-weight: 600;
        color: var(--bs-gray-900);
      }
    }

    .profit-info {
      font-size: 1rem;
      font-weight: 700;
    }
  }
}

// Desktop styles
@media (max-width: 992px) {
  .instrument-info > div:first-child {
    word-break: break-word;
  }
}
</style>
