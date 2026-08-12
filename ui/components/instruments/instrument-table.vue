<template>
  <data-table
    :items="instruments"
    :columns="columns"
    :is-loading="isLoading"
    :is-error="isError"
    :error-message="errorMessage"
    empty-message="No instruments found. Add a new instrument to get started."
    :sortable="true"
    :sort-state="sortState"
    :on-sort="onSort"
  >
    <template #footer>
      <tr
        v-if="instruments.length > 0"
        class="table-footer-totals hidden! md:table-row!"
        data-testid="instruments-totals-row"
      >
        <td class="font-bold">Total</td>
        <td></td>
        <td></td>
        <td class="font-bold whitespace-nowrap">
          <span :class="getTotalsChangeClass('totalValue')">
            {{ formatCurrencyWithSign(animatedTotalValue, 'EUR') }}
          </span>
        </td>
        <td class="font-bold whitespace-nowrap hidden! md:table-cell!">
          {{ formatCurrencyWithSign(totalInvested, 'EUR') }}
        </td>
        <td class="font-bold whitespace-nowrap profit-column">
          <span :class="[getProfitClass(totalProfit), getTotalsChangeClass('totalProfit')]">
            {{ formatProfit(animatedTotalProfit, 'EUR') }}
          </span>
        </td>
        <td class="font-bold whitespace-nowrap unrealized-column">
          <span
            :class="[
              getProfitClass(totalUnrealizedProfit),
              getTotalsChangeClass('totalUnrealizedProfit'),
            ]"
          >
            {{ formatProfit(animatedTotalUnrealizedProfit, 'EUR') }}
          </span>
        </td>
        <td class="font-bold whitespace-nowrap hidden! lg:table-cell! price-change-column">
          <span
            :class="[getProfitClass(totalChangeAmount), getTotalsChangeClass('totalChangeAmount')]"
          >
            {{ formatCurrencyWithSign(Math.abs(animatedTotalChangeAmount), 'EUR') }} /
            {{ Math.abs(animatedTotalChangePercent).toFixed(2) }}%
          </span>
        </td>
        <td class="font-bold whitespace-nowrap">
          <button
            type="button"
            class="xirr-trigger"
            :class="getTotalsChangeClass('totalXirr')"
            :disabled="totalXirr === null"
            :title="totalXirr === null ? '' : 'Show XIRR over 1M / 3M / 6M / 1Y / 2Y / 3Y'"
            @click="emit('show-xirr-windows')"
          >
            {{ totalXirr === null ? 'N/A' : formatPercentageFromDecimal(animatedTotalXirr) }}
          </button>
        </td>
        <td class="font-bold whitespace-nowrap hidden! xl:table-cell!">
          <button
            type="button"
            class="xirr-trigger"
            :disabled="totalAnnualReturn === null"
            :title="
              totalAnnualReturn === null
                ? ''
                : 'Show buy-and-hold annualized return over 1M / 3M / 6M / 1Y / 2Y / 3Y'
            "
            @click="emit('show-annual-windows')"
          >
            {{ totalAnnualReturn === null ? '-' : formatAnnualReturn(animatedTotalAnnualReturn) }}
          </button>
        </td>
        <td class="font-bold whitespace-nowrap hidden! xl:table-cell!">100.00%</td>
        <td class="font-bold whitespace-nowrap hidden! xl:table-cell!">
          {{ formatTer(totalTer) }}
        </td>
      </tr>
    </template>
    <template #mobile-card="{ item }">
      <div class="mobile-instrument-card">
        <div class="instrument-header">
          <div class="instrument-title">
            <h6 class="instrument-name">{{ item.name }}</h6>
            <span class="instrument-symbol">{{ item.symbol }}</span>
            <div v-if="item.platforms && item.platforms.length > 0" class="platform-tags mt-1">
              <span v-for="platform in item.platforms" :key="platform" class="badge mr-1">
                {{ formatPlatformName(platform) }}
              </span>
            </div>
          </div>
        </div>
        <div class="instrument-metrics">
          <div class="metric-group">
            <span class="metric-value">{{ formatQuantity(item.quantity) }}</span>
            <span class="metric-label">Quantity</span>
          </div>
          <div class="metric-group">
            <span class="metric-value">
              {{ formatPrice(item.currentPrice, item.baseCurrency) }}
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
        <div class="instrument-metrics secondary-metrics">
          <div class="metric-group">
            <span class="metric-value" :class="getProfitClass(item.profit || 0)">
              {{ formatProfit(item.profit || 0, item.baseCurrency) }}
            </span>
            <span class="metric-label">Profit</span>
          </div>
          <div class="metric-group">
            <span class="metric-value" :class="getProfitClass(item.unrealizedProfit || 0)">
              {{ formatProfit(item.unrealizedProfit || 0, item.baseCurrency) }}
            </span>
            <span class="metric-label">Unrealized</span>
          </div>
          <div class="metric-group">
            <span class="metric-value">{{ formatAnnualReturn(item.xirrAnnualReturn) }}</span>
            <span class="metric-label">Annual</span>
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
            <button
              type="button"
              class="total-value xirr-trigger-mobile"
              :class="getTotalsChangeClass('totalXirr')"
              :disabled="totalXirr === null"
              @click="emit('show-xirr-windows')"
            >
              {{ totalXirr === null ? 'N/A' : formatPercentageFromDecimal(animatedTotalXirr) }}
            </button>
          </div>
          <div class="total-item">
            <span class="total-label">ANNUAL</span>
            <button
              type="button"
              class="total-value xirr-trigger-mobile"
              :disabled="totalAnnualReturn === null"
              @click="emit('show-annual-windows')"
            >
              {{ totalAnnualReturn === null ? '-' : formatAnnualReturn(animatedTotalAnnualReturn) }}
            </button>
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
          <span class="block instrument-name">{{ item.name }}</span>
          <small class="block instrument-symbol">{{ item.symbol }}</small>
        </div>
        <div v-if="item.platforms && item.platforms.length > 0" class="platform-tags mt-1">
          <span v-for="platform in item.platforms" :key="platform" class="badge mr-1">
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
        {{ formatPrice(item.currentPrice, item.baseCurrency) }}
      </span>
    </template>

    <template #cell-priceChange="{ item }">
      <span
        :class="[
          getChangeClass(item.id, 'priceChangeAmount'),
          getProfitClass(item.priceChangeAmount),
        ]"
      >
        {{ formatPriceChange(item) }}
      </span>
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
          'whitespace-nowrap',
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
          'whitespace-nowrap',
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
      <span class="whitespace-nowrap">
        {{ formatAnnualReturn(item.xirrAnnualReturn) }}
      </span>
    </template>

    <template #cell-portfolioWeight="{ item }">
      <span class="whitespace-nowrap">{{ getPortfolioWeight(item) }}</span>
    </template>

    <template #cell-ter="{ item }">
      <span class="whitespace-nowrap">{{ formatTer(item.ter) }}</span>
    </template>
  </data-table>
</template>

<script setup lang="ts">
import { computed, toRef, watch } from 'vue'
import DataTable from '../shared/data-table.vue'
import { InstrumentDto } from '../../models/generated/domain-models'
import { instrumentColumns } from '../../config'
import {
  getProfitClass,
  formatCurrencyWithSign,
  formatPrice,
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
import type { SortState } from '../../composables/use-sortable-table'

interface Props {
  instruments: InstrumentDto[]
  portfolioXirr: number | null
  isLoading?: boolean
  isError?: boolean
  errorMessage?: string
  selectedPeriod: string
  sortState?: SortState
  onSort?: (key: string) => void
}

const props = withDefaults(defineProps<Props>(), {
  portfolioXirr: null,
  isLoading: false,
  isError: false,
  selectedPeriod: '24h',
})

const emit = defineEmits<{
  (e: 'show-xirr-windows'): void
  (e: 'show-annual-windows'): void
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
  totalAnnualReturn,
} = useInstrumentTotals(instrumentsRef)

const totalXirr = computed(() => props.portfolioXirr)
const totalXirrForAnimation = computed(() => props.portfolioXirr ?? 0)

watch(
  [totalValue, totalProfit, totalUnrealizedProfit, totalChangeAmount, totalXirrForAnimation],
  () => {
    trackTotalsChange({
      totalValue: totalValue.value,
      totalProfit: totalProfit.value,
      totalUnrealizedProfit: totalUnrealizedProfit.value,
      totalChangeAmount: totalChangeAmount.value,
      totalXirr: totalXirrForAnimation.value,
    })
  },
  { deep: true }
)

const animatedTotalValue = useNumberTransition(totalValue)
const animatedTotalProfit = useNumberTransition(totalProfit)
const animatedTotalUnrealizedProfit = useNumberTransition(totalUnrealizedProfit)
const animatedTotalChangeAmount = useNumberTransition(totalChangeAmount)
const animatedTotalXirr = useNumberTransition(totalXirrForAnimation)
const animatedTotalChangePercent = useNumberTransition(totalChangePercent)
const totalAnnualReturnForAnimation = computed(() => totalAnnualReturn.value ?? 0)
const animatedTotalAnnualReturn = useNumberTransition(totalAnnualReturnForAnimation)

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

<style scoped>
.platform-tags .badge {
  font-weight: 500;
  background-color: var(--color-ink-muted);
}

.table-footer-totals {
  background-color: var(--color-gray-100);
  border-top: 2px solid var(--color-hairline-strong);
}

.table-footer-totals td {
  padding: 1rem 0.75rem;
  font-weight: 600;
  color: var(--color-gray-700);
  vertical-align: middle;
}

.table-footer-totals td:first-child {
  font-size: 0.875rem;
  font-weight: 700;
  letter-spacing: 0.025em;
  text-transform: uppercase;
  color: var(--color-gray-800);
}

.xirr-trigger,
.xirr-trigger-mobile {
  margin: 0;
  padding: 0;
  background: none;
  border: none;
  font: inherit;
  color: inherit;
  text-decoration: underline dotted;
  text-underline-offset: 0.2rem;
  cursor: pointer;
}

.xirr-trigger:hover:not(:disabled),
.xirr-trigger-mobile:hover:not(:disabled) {
  color: var(--color-signal-indigo);
}

.xirr-trigger:disabled,
.xirr-trigger-mobile:disabled {
  text-decoration: none;
  cursor: default;
}

.mobile-totals-card {
  margin-bottom: 0.75rem;
  padding: 1rem;
  background: var(--color-surface);
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-container);
  box-shadow: var(--shadow-card);
}

.totals-header {
  margin-bottom: 0.5rem;
  text-align: center;
}

.totals-title {
  margin: 0;
  font-size: 0.875rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: var(--color-gray-800);
}

.totals-divider {
  margin: 0.75rem 0;
  border: 0;
  border-top: 1px solid var(--color-gray-200);
  opacity: 1;
}

.totals-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.total-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  text-align: center;
}

.total-label {
  font-size: 0.6875rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-gray-600);
}

.total-value {
  font-size: 1.125rem;
  font-weight: 700;
  line-height: 1.2;
  color: var(--color-gray-900);
}

.total-value.text-gain {
  color: var(--color-gain);
}

.total-value.text-loss {
  color: var(--color-loss);
}

.mobile-instrument-card {
  padding: 0.75rem;
}

.mobile-instrument-card .instrument-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

.mobile-instrument-card .instrument-title {
  flex: 1;
}

.mobile-instrument-card .instrument-name {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  line-height: 1.2;
  color: var(--color-gray-900);
}

.mobile-instrument-card .instrument-symbol {
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--color-gray-600);
}

.mobile-instrument-card .instrument-header .btn-link {
  color: var(--color-gray-600);
  opacity: 0.7;
  transition: opacity var(--transition-base);
}

.mobile-instrument-card .instrument-header .btn-link:hover {
  opacity: 1;
}

.mobile-instrument-card .instrument-metrics {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.75rem 0.5rem;
  margin-bottom: 0.75rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--color-gray-200);
}

.mobile-instrument-card .instrument-metrics.secondary-metrics {
  margin-bottom: 0.75rem;
  padding-bottom: 0;
  border-bottom: none;
}

.mobile-instrument-card .metric-group {
  flex: 1;
  min-width: 0;
  text-align: center;
}

.mobile-instrument-card .metric-value {
  display: block;
  margin-bottom: 0.125rem;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--color-gray-900);
}

.mobile-instrument-card .metric-value.text-gain {
  color: var(--color-gain);
}

.mobile-instrument-card .metric-value.text-loss {
  color: var(--color-loss);
}

.mobile-instrument-card .metric-label {
  display: block;
  font-size: 0.6875rem;
  letter-spacing: 0.025em;
  text-transform: uppercase;
  color: var(--color-gray-600);
}

.mobile-instrument-card .instrument-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.mobile-instrument-card .value-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.mobile-instrument-card .value-label {
  font-size: 0.75rem;
  color: var(--color-gray-600);
}

.mobile-instrument-card .value-amount {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--color-gray-900);
}

@media (max-width: 992px) {
  .instrument-info > div:first-child {
    word-break: break-word;
  }
}

@media (max-width: 992px) and (orientation: landscape) {
  :deep(.profit-column),
  :deep(.price-change-column) {
    display: none !important;
  }

  .mobile-totals-card .total-profit-item,
  .mobile-totals-card .total-price-change-item {
    display: none !important;
  }
}

@media (max-height: 500px) {
  :deep(.profit-column),
  :deep(.price-change-column) {
    display: none !important;
  }

  .mobile-totals-card .total-profit-item,
  .mobile-totals-card .total-price-change-item {
    display: none !important;
  }
}

@keyframes pulse-increase {
  0% {
    background-color: transparent;
  }

  50% {
    background-color: rgb(33 197 93 / 0.2);
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
    background-color: rgb(220 53 69 / 0.2);
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
