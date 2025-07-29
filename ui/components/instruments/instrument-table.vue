<template>
  <data-table
    :items="instruments"
    :columns="columns"
    :is-loading="isLoading"
    :is-error="isError"
    :error-message="errorMessage"
    empty-message="No instruments found. Add a new instrument to get started."
  >
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
            <span :class="getProfitClass(item.profit)">
              {{
                item.profit !== null && item.profit !== undefined
                  ? (item.profit >= 0 ? '+' : '') +
                    getCurrencySymbol(item.baseCurrency) +
                    Math.abs(item.profit).toFixed(2)
                  : getCurrencySymbol(item.baseCurrency) + '0.00'
              }}
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
      <span :class="getProfitClass(item.profit)" class="profit-display text-nowrap">
        {{
          item.profit !== null && item.profit !== undefined
            ? (item.profit >= 0 ? '+' : '') +
              getCurrencySymbol(item.baseCurrency) +
              Math.abs(item.profit).toFixed(2)
            : getCurrencySymbol(item.baseCurrency) + '0.00'
        }}
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
import DataTable from '../shared/data-table.vue'
import BaseIcon from '../shared/base-icon.vue'
import { Instrument } from '../../models/instrument'
import { instrumentColumns } from '../../config'
import {
  getProfitClass,
  formatCurrencyWithSign,
  getCurrencySymbol,
  formatQuantity,
  formatPercentageFromDecimal,
} from '../../utils/formatters'

interface Props {
  instruments: Instrument[]
  isLoading?: boolean
  isError?: boolean
  errorMessage?: string
}

withDefaults(defineProps<Props>(), {
  isLoading: false,
  isError: false,
})

defineEmits<{
  edit: [instrument: Instrument]
}>()

const columns = instrumentColumns
</script>

<style scoped lang="scss">
@import '../../styles/shared-table.scss';

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
