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
        <td></td>
        <td></td>
        <td class="fw-bold text-nowrap">{{ formatCurrencyWithSign(totalValue, 'EUR') }}</td>
        <td class="fw-bold text-nowrap">{{ formatCurrencyWithSign(totalInvested, 'EUR') }}</td>
        <td class="fw-bold text-nowrap">
          <span :class="getProfitClass(totalProfit)">
            {{ formatProfit(totalProfit, 'EUR') }}
          </span>
        </td>
        <td></td>
      </tr>
    </template>
    <template #mobile-card="{ item }">
      <div class="mobile-instrument-card">
        <div class="instrument-header">
          <div class="instrument-title">
            <h6 class="instrument-name">{{ item.name }}</h6>
            <span class="instrument-symbol">{{ item.symbol }}</span>
          </div>
          <button class="btn btn-sm btn-link p-0" @click="$emit('edit', item)" title="Edit">
            <base-icon name="pencil" :size="16" />
          </button>
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
          <h6 class="mb-0">Total Portfolio</h6>
        </div>
        <div class="totals-content">
          <div class="total-item">
            <span class="total-label">Value</span>
            <span class="total-value">{{ formatCurrencyWithSign(totalValue, 'EUR') }}</span>
          </div>
          <div class="total-item">
            <span class="total-label">Invested</span>
            <span class="total-value">{{ formatCurrencyWithSign(totalInvested, 'EUR') }}</span>
          </div>
          <div class="total-item">
            <span class="total-label">Profit</span>
            <span class="total-value" :class="getProfitClass(totalProfit)">
              {{ formatProfit(totalProfit, 'EUR') }}
            </span>
          </div>
        </div>
      </div>
    </template>

    <template #cell-instrument="{ item }">
      <span class="instrument-info">
        <span class="d-block instrument-name">{{ item.name }}</span>
        <small class="d-block text-muted instrument-symbol">{{ item.symbol }}</small>
      </span>
    </template>

    <template #cell-type="{ item }">
      {{ item.type || item.category || '-' }}
    </template>

    <template #cell-currentPrice="{ item }">
      {{ formatCurrencyWithSign(item.currentPrice, item.baseCurrency) }}
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

    <template #actions="{ item }">
      <div class="action-buttons">
        <button
          class="btn btn-sm btn-ghost btn-secondary"
          @click="$emit('edit', item)"
          title="Edit"
        >
          <base-icon name="pencil" :size="14" />
          <span class="ms-1 d-inline d-lg-none">Edit</span>
        </button>
      </div>
    </template>
  </data-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DataTable from '../shared/data-table.vue'
import BaseIcon from '../shared/base-icon.vue'
import { Instrument } from '../../models/instrument'
import { instrumentColumns } from '../../config'
import {
  getProfitClass,
  formatCurrencyWithSign,
  formatQuantity,
  formatPercentageFromDecimal,
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

const getItemProfit = (item: Instrument): number => {
  const value = item.currentValue || 0
  const invested = item.totalInvestment || 0
  return value - invested
}

const formatProfit = (amount: number, currency: string | undefined): string => {
  const sign = amount >= 0 ? '+' : '-'
  return sign + formatCurrencyWithSign(Math.abs(amount), currency || 'EUR')
}
</script>

<style scoped lang="scss">
@import '../../styles/shared-table.scss';

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
  margin: 1rem 0.75rem;
  padding: 1rem;
  background: linear-gradient(135deg, var(--bs-gray-100) 0%, white 100%);
  border: 1px solid var(--bs-gray-300);
  border-radius: 0.5rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);

  .totals-header {
    padding-bottom: 0.75rem;
    margin-bottom: 0.75rem;
    border-bottom: 2px solid var(--bs-gray-300);

    h6 {
      font-weight: 700;
      text-transform: uppercase;
      font-size: 0.875rem;
      letter-spacing: 0.025em;
      color: var(--bs-gray-800);
    }
  }

  .totals-content {
    display: flex;
    justify-content: space-between;
    gap: 1rem;

    .total-item {
      flex: 1;
      text-align: center;

      .total-label {
        display: block;
        font-size: 0.75rem;
        font-weight: 500;
        color: var(--bs-gray-600);
        text-transform: uppercase;
        letter-spacing: 0.025em;
        margin-bottom: 0.25rem;
      }

      .total-value {
        display: block;
        font-size: 1.125rem;
        font-weight: 700;
        color: var(--bs-gray-900);
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
    justify-content: space-between;
    margin-bottom: 0.75rem;
    padding-bottom: 0.75rem;
    border-bottom: 1px solid var(--bs-gray-200);

    .metric-group {
      text-align: center;
      flex: 1;

      .metric-value {
        display: block;
        font-size: 0.875rem;
        font-weight: 600;
        color: var(--bs-gray-900);
        margin-bottom: 0.125rem;
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
