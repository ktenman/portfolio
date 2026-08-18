<template>
  <div class="mx-auto mt-4 w-full max-w-app px-3">
    <portfolio-actions
      :is-loading="isLoading"
      :is-recalculating="isRecalculating"
      @recalculate="handleRecalculate"
    >
      <template #title-suffix>
        <filter-toggle
          v-if="availablePlatforms.length > 0"
          v-model="filtersOpen"
          :selected="selectedPlatforms.length"
          :available="availablePlatforms.length"
        />
      </template>
    </portfolio-actions>

    <platform-filter
      v-if="filtersOpen && availablePlatforms.length > 0"
      class="mb-6"
      :available="availablePlatforms"
      :selected="selectedPlatforms"
      @toggle="togglePlatform"
      @toggle-all="toggleAllPlatforms"
    />

    <div v-if="viewState === 'LOADING'">
      <skeleton-loader type="card" class="mb-6" />
      <skeleton-loader type="table" :rows="10" :columns="5" />
    </div>

    <div v-else-if="viewState === 'ERROR'" class="alert alert-danger" role="alert">
      {{ error }}
    </div>

    <div v-else-if="viewState === 'EMPTY'" class="alert alert-info" role="alert">
      No portfolio summary data found.
    </div>

    <template v-else>
      <div
        v-if="showRecalculationMessage"
        class="alert alert-info alert-dismissible mt-4"
        role="alert"
      >
        {{ recalculationMessage }}
        <button
          type="button"
          class="btn-close"
          @click="recalculationMessage = ''"
          aria-label="Close"
        ></button>
      </div>

      <header v-if="latestSummary" class="portfolio-headline">
        <h1>{{ formatCurrencyWithSymbol(latestSummary.totalValue) }}</h1>
        <div class="headline-meta">
          <range-change-header
            v-if="rangeChange"
            :amount="rangeChange.changeAmount"
            :percent="rangeChange.changePercent"
          />
          <span v-if="staleAsOf" class="headline-asof">as of {{ staleAsOf }}</span>
        </div>
      </header>

      <div class="chart-frame">
        <portfolio-chart :key="chartKey" :data="processedChartData" />
        <div v-if="isRangeLoading" class="chart-veil">
          <loading-spinner message="Loading chart" />
        </div>
      </div>

      <div v-if="rangeError" class="alert alert-warning mt-3" role="alert">
        Could not load the {{ selectedRange }} chart range: {{ rangeError }}
      </div>

      <chart-range-filter class="mt-3" :selected="selectedRange" @select="selectedRange = $event" />

      <data-table
        :items="sortedItems"
        :columns="summaryColumns"
        :row-class="getSummaryRowClass"
        :sortable="true"
        :sort-state="sortState"
        :on-sort="toggleSort"
        class="mt-10"
      >
        <template #cell-totalProfitChange24h="{ value, item }">
          <span v-if="value && Math.abs(value) > 0.01" :class="getGainLossClass(value)">
            {{ format24hChange(item.totalProfitChange24h) }}
            <span class="change-percentage">{{ format24hChangePercentage(item) }}</span>
          </span>
        </template>
      </data-table>

      <div v-if="isFetching" class="mt-4 text-center">
        <skeleton-loader type="text" :lines="2" />
      </div>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { defineAsyncComponent, computed, h, ref, watch } from 'vue'
import { useInfiniteScroll, useLocalStorage, useWindowSize } from '@vueuse/core'
import { useQuery } from '@tanstack/vue-query'
import { usePortfolioSummaryQuery } from '../composables/use-portfolio-summary-query'
import { usePortfolioChart } from '../composables/use-portfolio-chart'
import { useConfirm } from '../composables/use-confirm'
import { useSortableTable } from '../composables/use-sortable-table'
import { usePlatformFilter } from '../composables/use-platform-filter'
import { useChartRange } from '../composables/use-time-range'
import { useAuthState } from '../composables/use-auth-state'
import PortfolioActions from './portfolio/portfolio-actions.vue'
import ChartRangeFilter from './portfolio/chart-range-filter.vue'
import RangeChangeHeader from './portfolio/range-change-header.vue'
import DataTable, { type ColumnDefinition } from './shared/data-table.vue'
import SkeletonLoader from './shared/skeleton-loader.vue'
import LoadingSpinner from './shared/loading-spinner.vue'
import PlatformFilter from './shared/platform-filter.vue'
import FilterToggle from './shared/filter-toggle.vue'
import { transactionsService } from '../services/transactions-service'
import { STORAGE_KEYS } from '../constants'
import { REFETCH_INTERVALS } from '../constants/api'
import {
  formatCurrencyWithSymbol,
  formatDate,
  formatPercentageFromDecimal,
  formatSignedPercent,
  getGainLossClass,
} from '../utils/formatters'
import type { PortfolioSummaryDto } from '../models/generated/domain-models'

const PortfolioChart = defineAsyncComponent({
  loader: () => import('./portfolio/portfolio-chart.vue'),
  loadingComponent: () => h(LoadingSpinner, { containerClass: 'min-h-48' }),
  errorComponent: () =>
    h(
      'div',
      { class: 'alert alert-warning', role: 'alert' },
      'Could not load the chart. Refresh the page to try again.'
    ),
})

type ViewState = 'LOADING' | 'ERROR' | 'EMPTY' | 'SUCCESS'

const { isAuthenticated } = useAuthState()

const { data: platformsData } = useQuery({
  queryKey: ['transaction-platforms'],
  queryFn: () => transactionsService.getPlatforms(),
  enabled: isAuthenticated,
  refetchInterval: REFETCH_INTERVALS.PLATFORMS,
})

const availablePlatforms = computed(() => platformsData.value ?? [])

const { selectedPlatforms, togglePlatform, toggleAllPlatforms } = usePlatformFilter(
  STORAGE_KEYS.SELECTED_SUMMARY_PLATFORMS,
  availablePlatforms
)

const filtersOpen = useLocalStorage(STORAGE_KEYS.SUMMARY_FILTERS_OPEN, true)

const selectedRange = useChartRange()

const {
  summaries,
  chartSummaries,
  rangeChange,
  reversedSummaries,
  isLoading,
  isRecalculating,
  isFetching,
  isRangeLoading,
  error,
  rangeError,
  recalculationMessage,
  recalculate,
  fetchSummaries,
  hasMoreData,
} = usePortfolioSummaryQuery(selectedPlatforms, selectedRange)

const { sortedItems, sortState, toggleSort } = useSortableTable(reversedSummaries, 'date', 'desc')

const { processedChartData } = usePortfolioChart(chartSummaries)

const { confirm } = useConfirm()

const { width } = useWindowSize()
const chartKey = ref(0)

watch(width, () => {
  chartKey.value++
})

const viewState = computed<ViewState>(() => {
  if (isLoading.value) return 'LOADING'
  if (error.value) return 'ERROR'
  if (!summaries.value || summaries.value.length === 0) return 'EMPTY'
  return 'SUCCESS'
})

const showRecalculationMessage = computed(() => !!recalculationMessage.value)

const latestSummary = computed(() => reversedSummaries.value[0] ?? null)

const today = () => new Date().toISOString().split('T')[0]

const staleAsOf = computed(() => {
  const date = latestSummary.value?.date
  if (!date || date === today()) return null
  return formatDate(date)
})

const format24hChange = (value: number | null) => {
  if (value === null || value === 0 || Math.abs(value) <= 0.01) {
    return ''
  }
  return formatCurrencyWithSymbol(value)
}

const format24hChangePercentage = (summary: PortfolioSummaryDto) => {
  const change = summary.totalProfitChange24h
  if (change === null || Math.abs(change) <= 0.01) {
    return ''
  }
  const previousValue = summary.totalValue - change
  if (previousValue <= 0) {
    return ''
  }
  return `(${formatSignedPercent((change / previousValue) * 100)})`
}

const summaryColumns: ColumnDefinition[] = [
  { key: 'date', label: 'Date', formatter: formatDate },
  {
    key: 'xirrAnnualReturn',
    label: 'XIRR Annual Return',
    formatter: formatPercentageFromDecimal,
    class: 'text-right!',
  },
  {
    key: 'earningsPerDay',
    label: 'Earnings Per Day',
    formatter: formatCurrencyWithSymbol,
    class: 'hidden! md:table-cell! text-right!',
    hideOnMobile: true,
  },
  {
    key: 'earningsPerMonth',
    label: 'Earnings Per Month',
    formatter: formatCurrencyWithSymbol,
    class: 'text-right!',
  },
  {
    key: 'unrealizedProfit',
    label: 'Unrealized Profit',
    formatter: formatCurrencyWithSymbol,
    class: 'hidden! md:table-cell! text-right!',
    hideOnMobile: true,
  },
  {
    key: 'totalProfit',
    label: 'Total Profit',
    formatter: formatCurrencyWithSymbol,
    class: 'text-right!',
  },
  {
    key: 'totalProfitChange24h',
    label: '24h Change',
    formatter: format24hChange,
    class: 'text-right!',
  },
  {
    key: 'totalValue',
    label: 'Total Value',
    formatter: formatCurrencyWithSymbol,
    class: 'text-right!',
  },
]

const getSummaryRowClass = (summary: any, index: number) => ({
  'font-weight-bold': index === 0 && summary.date === today(),
})

useInfiniteScroll(
  window,
  async () => {
    if (isFetching.value || !hasMoreData?.value) return
    await fetchSummaries()
  },
  { distance: 100 }
)

const handleRecalculate = async () => {
  const shouldProceed = await confirm({
    title: 'Recalculate Portfolio Data',
    message:
      'This will delete all current summary data and recalculate it from scratch. This operation may take some time. Continue?',
    confirmText: 'Recalculate',
    cancelText: 'Cancel',
    confirmClass: 'btn-danger',
  })

  if (shouldProceed) {
    await recalculate()
  }
}
</script>

<style scoped>
.chart-frame {
  position: relative;
  min-height: 12rem;
}

.portfolio-headline {
  margin-bottom: 1.5rem;
}

.portfolio-headline h1 {
  margin: 0;
  line-height: 1.05;
}

.headline-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.5rem 0.75rem;
  margin-top: 0.375rem;
}

.headline-asof {
  font-size: var(--text-sm);
  color: var(--color-ink-soft);
}

.chart-veil {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--color-surface) 72%, transparent);
}

.change-percentage {
  margin-left: 0.25rem;
  font-size: 0.85em;
}

@media (max-width: 575px) {
  :deep(.table) {
    font-size: 12px;
  }

  :deep(.hide-on-mobile) {
    display: none;
  }
}
</style>
