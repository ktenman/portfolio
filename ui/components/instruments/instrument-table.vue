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
        <td class="fw-bold text-nowrap">
          <span :class="getTotalsChangeClass('totalValue')">
            {{ formatCurrencyWithSign(animatedTotalValue, 'EUR') }}
          </span>
        </td>
        <td class="fw-bold text-nowrap">{{ formatCurrencyWithSign(totalInvested, 'EUR') }}</td>
        <td class="fw-bold text-nowrap profit-column">
          <span :class="[getProfitClass(totalProfit), getTotalsChangeClass('totalProfit')]">
            {{ formatProfit(animatedTotalProfit, 'EUR') }}
          </span>
        </td>
        <td class="fw-bold text-nowrap unrealized-column">
          <span
            :class="[
              getProfitClass(totalUnrealizedProfit),
              getTotalsChangeClass('totalUnrealizedProfit'),
            ]"
          >
            {{ formatProfit(animatedTotalUnrealizedProfit, 'EUR') }}
          </span>
        </td>
        <td class="fw-bold text-nowrap price-change-column">
          <span
            :class="[getProfitClass(totalChangeAmount), getTotalsChangeClass('totalChangeAmount')]"
          >
            {{ formatCurrencyWithSign(Math.abs(animatedTotalChangeAmount), 'EUR') }} /
            {{ Math.abs(animatedTotalChangePercent).toFixed(2) }}%
          </span>
        </td>
        <td class="fw-bold text-nowrap">
          <span :class="getTotalsChangeClass('totalXirr')">
            {{ formatPercentageFromDecimal(animatedTotalXirr) }}
          </span>
        </td>
        <td class="fw-bold text-nowrap">-</td>
        <td class="fw-bold text-nowrap">100.00%</td>
        <td class="fw-bold text-nowrap">{{ formatTer(totalTer) }}</td>
        <td></td>
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
          <button
            class="btn btn-sm btn-ghost btn-secondary btn-table-action"
            @click="$emit('edit', item)"
            title="Edit"
          >
            <PenLine :size="16" />
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
          <div
            v-if="item.priceChangeAmount !== undefined && item.priceChangeAmount !== null"
            class="metric-group"
          >
            <span class="metric-value" :class="getProfitClass(item.priceChangeAmount)">
              {{ formatCurrencyWithSign(Math.abs(item.priceChangeAmount), item.baseCurrency) }}
            </span>
            <span class="metric-label">{{ selectedPeriod.toUpperCase() }}</span>
          </div>
        </div>
        <div class="instrument-footer">
          <div class="value-info">
            <span class="value-label">Value</span>
            <span class="value-amount">
              {{ formatCurrencyWithSign(item.currentValue, item.baseCurrency) }}
            </span>
          </div>
          <div class="value-info">
            <span class="value-label">Weight</span>
            <span class="value-amount">{{ getPortfolioWeight(item) }}</span>
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
            <span class="total-value" :class="getTotalsChangeClass('totalValue')">
              {{ formatCurrencyWithSign(animatedTotalValue, 'EUR') }}
            </span>
          </div>
          <div class="total-item">
            <span class="total-label">INVESTED</span>
            <span class="total-value">{{ formatCurrencyWithSign(totalInvested, 'EUR') }}</span>
          </div>
          <div class="total-item total-profit-item">
            <span class="total-label">PROFIT</span>
            <span
              class="total-value"
              :class="[getProfitClass(totalProfit), getTotalsChangeClass('totalProfit')]"
            >
              {{ formatProfit(animatedTotalProfit, 'EUR') }}
            </span>
          </div>
          <div class="total-item total-unrealized-item">
            <span class="total-label">UNREALIZED</span>
            <span
              class="total-value"
              :class="[
                getProfitClass(totalUnrealizedProfit),
                getTotalsChangeClass('totalUnrealizedProfit'),
              ]"
            >
              {{ formatProfit(animatedTotalUnrealizedProfit, 'EUR') }}
            </span>
          </div>
          <div class="total-item total-price-change-item">
            <span class="total-label">{{ selectedPeriod.toUpperCase() }}</span>
            <span
              class="total-value"
              :class="[
                getProfitClass(totalChangeAmount),
                getTotalsChangeClass('totalChangeAmount'),
              ]"
            >
              {{ formatCurrencyWithSign(Math.abs(animatedTotalChangeAmount), 'EUR') }} /
              {{ Math.abs(animatedTotalChangePercent).toFixed(2) }}%
            </span>
          </div>
          <div class="total-item">
            <span class="total-label">XIRR</span>
            <span class="total-value" :class="getTotalsChangeClass('totalXirr')">
              {{ formatPercentageFromDecimal(animatedTotalXirr) }}
            </span>
          </div>
          <div class="total-item">
            <span class="total-label">TER</span>
            <span class="total-value">{{ formatTer(totalTer) }}</span>
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
      {{ formatAcronym(item.category) }}
    </template>

    <template #cell-currentPrice="{ item }">
      <span :class="getChangeClass(item.id, 'currentPrice')">
        {{ formatCurrencyWithSign(item.currentPrice, item.baseCurrency) }}
      </span>
    </template>

    <template #cell-priceChange="{ item }">
      <span
        :class="getChangeClass(item.id, 'priceChangeAmount')"
        v-html="formatPriceChange(item)"
      ></span>
    </template>

    <template #cell-totalInvestment="{ item }">
      {{ formatCurrencyWithSign(item.totalInvestment, item.baseCurrency) }}
    </template>

    <template #cell-currentValue="{ item }">
      <span :class="getChangeClass(item.id, 'currentValue')">
        {{ formatCurrencyWithSign(item.currentValue, item.baseCurrency) }}
      </span>
    </template>

    <template #cell-profit="{ item }">
      <span
        :class="[
          getProfitClass(item.profit || 0),
          getChangeClass(item.id, 'profit'),
          'profit-display',
          'text-nowrap',
        ]"
      >
        {{ formatProfit(item.profit || 0, item.baseCurrency) }}
      </span>
    </template>

    <template #cell-unrealizedProfit="{ item }">
      <span
        :class="[
          getProfitClass(item.unrealizedProfit || 0),
          getChangeClass(item.id, 'unrealizedProfit'),
          'profit-display',
          'text-nowrap',
        ]"
      >
        {{ formatProfit(item.unrealizedProfit || 0, item.baseCurrency) }}
      </span>
    </template>

    <template #cell-xirr="{ item }">
      <span :class="getChangeClass(item.id, 'xirr')">
        {{ formatPercentageFromDecimal(item.xirr) }}
      </span>
    </template>

    <template #cell-xirrAnnualReturn="{ item }">
      <span class="text-nowrap">
        {{ formatAnnualReturn(item.xirrAnnualReturn) }}
      </span>
    </template>

    <template #cell-portfolioWeight="{ item }">
      <span class="text-nowrap">{{ getPortfolioWeight(item) }}</span>
    </template>

    <template #cell-ter="{ item }">
      <span class="text-nowrap">{{ formatTer(item.ter) }}</span>
    </template>

    <template #actions="{ item }">
      <div class="action-buttons">
        <button
          class="btn btn-sm btn-ghost btn-secondary btn-table-action"
          @click="$emit('edit', item)"
          title="Edit"
        >
          <PenLine :size="16" />
        </button>
      </div>
    </template>
  </data-table>
</template>

<script setup lang="ts">
import { computed, toRef, watch } from 'vue'
import { PenLine } from 'lucide-vue-next'
import DataTable from '../shared/data-table.vue'
import { InstrumentDto } from '../../models/generated/domain-models'
import { instrumentColumns } from '../../config'
import {
  getProfitClass,
  formatCurrencyWithSign,
  formatQuantity,
  formatPercentageFromDecimal,
  formatPriceChange,
  formatAcronym,
} from '../../utils/formatters'
import { formatPlatformName } from '../../utils/platform-utils'
import { formatProfit, calculatePortfolioWeight } from '../../utils/instrument-formatters'
import { useValueChangeAnimation } from '../../composables/use-value-change-animation'
import { useNumberTransition } from '../../composables/use-number-transition'
import { useInstrumentTotals } from '../../composables/use-instrument-totals'

interface Props {
  instruments: InstrumentDto[]
  portfolioXirr: number
  isLoading?: boolean
  isError?: boolean
  errorMessage?: string
  selectedPeriod: string
}

const props = withDefaults(defineProps<Props>(), {
  portfolioXirr: 0,
  isLoading: false,
  isError: false,
  selectedPeriod: '24h',
})

defineEmits<{
  edit: [instrument: InstrumentDto]
}>()

const instrumentsRef = toRef(props, 'instruments')
const { getChangeClass, trackTotalsChange, getTotalsChangeClass } =
  useValueChangeAnimation(instrumentsRef)

const columns = computed(() =>
  instrumentColumns.map(col =>
    col.key === 'priceChange' ? { ...col, label: props.selectedPeriod.toUpperCase() } : col
  )
)

const {
  totalInvested,
  totalValue,
  totalProfit,
  totalUnrealizedProfit,
  totalChangeAmount,
  totalChangePercent,
  totalTer,
} = useInstrumentTotals(instrumentsRef)

const totalXirr = computed(() => props.portfolioXirr)

watch(
  [totalValue, totalProfit, totalUnrealizedProfit, totalChangeAmount, totalXirr],
  () => {
    trackTotalsChange({
      totalValue: totalValue.value,
      totalProfit: totalProfit.value,
      totalUnrealizedProfit: totalUnrealizedProfit.value,
      totalChangeAmount: totalChangeAmount.value,
      totalXirr: totalXirr.value,
    })
  },
  { deep: true }
)

const animatedTotalValue = useNumberTransition(totalValue)
const animatedTotalProfit = useNumberTransition(totalProfit)
const animatedTotalUnrealizedProfit = useNumberTransition(totalUnrealizedProfit)
const animatedTotalChangeAmount = useNumberTransition(totalChangeAmount)
const animatedTotalXirr = useNumberTransition(totalXirr)
const animatedTotalChangePercent = useNumberTransition(totalChangePercent)

const getPortfolioWeight = (instrument: InstrumentDto): string => {
  return calculatePortfolioWeight(instrument.currentValue || 0, totalValue.value)
}

const formatTer = (ter: number | null | undefined): string => {
  if (ter === null || ter === undefined) return '-'
  return `${ter.toFixed(2)}%`
}

const formatAnnualReturn = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return '-'
  return formatPercentageFromDecimal(value)
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
    gap: 1rem;

    .value-info {
      display: flex;
      align-items: center;
      gap: 0.5rem;

      .value-label {
        font-size: 0.75rem;
        color: var(--bs-gray-600);
      }

      .value-amount {
        font-size: 0.9375rem;
        font-weight: 600;
        color: var(--bs-gray-900);
      }
    }
  }
}

// Desktop styles
@media (max-width: 992px) {
  .instrument-info > div:first-child {
    word-break: break-word;
  }
}

// Mobile landscape: hide profit and price change columns from desktop table
@media (max-width: 992px) and (orientation: landscape) {
  :deep(.profit-column),
  :deep(.price-change-column) {
    display: none !important;
  }

  .mobile-totals-card {
    .total-profit-item,
    .total-price-change-item {
      display: none !important;
    }
  }
}

// Also hide on short screens (likely landscape mobile)
@media (max-height: 500px) {
  :deep(.profit-column),
  :deep(.price-change-column) {
    display: none !important;
  }

  .mobile-totals-card {
    .total-profit-item,
    .total-price-change-item {
      display: none !important;
    }
  }
}

@keyframes pulse-increase {
  0% {
    background-color: transparent;
  }
  50% {
    background-color: rgba(34, 197, 94, 0.2);
  }
  100% {
    background-color: transparent;
  }
}

@keyframes pulse-decrease {
  0% {
    background-color: transparent;
  }
  50% {
    background-color: rgba(239, 68, 68, 0.2);
  }
  100% {
    background-color: transparent;
  }
}

.value-increase {
  animation: pulse-increase 3s ease-in-out;
  transition: background-color 3s ease-in-out;
}

.value-decrease {
  animation: pulse-decrease 3s ease-in-out;
  transition: background-color 3s ease-in-out;
}
</style>
