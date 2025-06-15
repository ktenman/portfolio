<template>
  <div class="container mt-2">
    <portfolio-actions
      :is-loading="isLoading"
      :is-recalculating="isRecalculating"
      @recalculate="handleRecalculate"
    />

    <div v-if="viewState === 'LOADING'" class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
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
        class="alert alert-info alert-dismissible fade show mt-3"
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

      <portfolio-chart :key="chartKey" :data="processedChartData" />

      <div v-if="showListView" class="list-view mt-3">
        <div
          v-for="(summary, index) in reversedSummaries"
          :key="summary.date"
          class="list-item p-3 mb-2 border rounded"
          :class="getSummaryRowClass(summary, index)"
        >
          <div class="row align-items-center">
            <div class="col-md-2 col-6 mb-2 mb-md-0">
              <small class="text-muted d-block">Date</small>
              <strong>{{ formatDate(summary.date) }}</strong>
            </div>
            <div class="col-md-2 col-6 mb-2 mb-md-0">
              <small class="text-muted d-block">XIRR Annual Return</small>
              <strong>{{ formatPercentageFromDecimal(summary.xirrAnnualReturn) }}</strong>
            </div>
            <div class="col-md-2 col-6 mb-2 mb-md-0 d-none d-md-block">
              <small class="text-muted d-block">Earnings Per Day</small>
              <strong>{{ formatCurrencyWithSymbol(summary.earningsPerDay) }}</strong>
            </div>
            <div class="col-md-2 col-6 mb-2 mb-md-0">
              <small class="text-muted d-block">Earnings Per Month</small>
              <strong>{{ formatCurrencyWithSymbol(summary.earningsPerMonth) }}</strong>
            </div>
            <div class="col-md-2 col-6 mb-2 mb-md-0">
              <small class="text-muted d-block">Total Profit</small>
              <strong>{{ formatCurrencyWithSymbol(summary.totalProfit) }}</strong>
            </div>
            <div class="col-md-2 col-6 mb-2 mb-md-0">
              <small class="text-muted d-block">Total Value</small>
              <strong>{{ formatCurrencyWithSymbol(summary.totalValue) }}</strong>
            </div>
          </div>
        </div>
      </div>

      <data-table
        v-else
        :items="reversedSummaries"
        :columns="summaryColumns"
        :row-class="getSummaryRowClass"
        class="mt-3"
      />

      <div v-if="isFetching" class="text-center mt-3">
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">Loading more data...</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { defineAsyncComponent, computed, ref, watch } from 'vue'
import { useInfiniteScroll, useScreenOrientation, useWindowSize } from '@vueuse/core'
import { usePortfolioSummaryQuery } from '../composables/use-portfolio-summary-query'
import { usePortfolioChart } from '../composables/use-portfolio-chart'
import { useConfirm } from '../composables/use-confirm'
import PortfolioActions from './portfolio/portfolio-actions.vue'
import DataTable, { type ColumnDefinition } from './shared/data-table.vue'
import {
  formatCurrencyWithSymbol,
  formatDate,
  formatPercentageFromDecimal,
} from '../utils/formatters'

const PortfolioChart = defineAsyncComponent(() => import('./portfolio/portfolio-chart.vue'))

type ViewState = 'LOADING' | 'ERROR' | 'EMPTY' | 'SUCCESS'

const {
  summaries,
  reversedSummaries,
  isLoading,
  isRecalculating,
  isFetching,
  error,
  recalculationMessage,
  recalculate,
  fetchSummaries,
  hasMoreData,
} = usePortfolioSummaryQuery()

const { processedChartData } = usePortfolioChart(summaries)

const { confirm } = useConfirm()

const { orientation } = useScreenOrientation()
const { width } = useWindowSize()
const chartKey = ref(0)

const isLandscape = computed(
  () => orientation.value === 'landscape-primary' || orientation.value === 'landscape-secondary'
)
const isMobile = computed(() => width.value <= 666)
const showListView = computed(() => isMobile.value && isLandscape.value)

watch([orientation, width], () => {
  chartKey.value++
})

const viewState = computed<ViewState>(() => {
  if (isLoading.value) return 'LOADING'
  if (error.value) return 'ERROR'
  if (!summaries.value || summaries.value.length === 0) return 'EMPTY'
  return 'SUCCESS'
})

const showRecalculationMessage = computed(() => !!recalculationMessage.value)

const summaryColumns: ColumnDefinition[] = [
  { key: 'date', label: 'Date', formatter: formatDate },
  { key: 'xirrAnnualReturn', label: 'XIRR Annual Return', formatter: formatPercentageFromDecimal },
  {
    key: 'earningsPerDay',
    label: 'Earnings Per Day',
    formatter: formatCurrencyWithSymbol,
    class: 'hide-on-mobile',
  },
  { key: 'earningsPerMonth', label: 'Earnings Per Month', formatter: formatCurrencyWithSymbol },
  { key: 'totalProfit', label: 'Total Profit', formatter: formatCurrencyWithSymbol },
  { key: 'totalValue', label: 'Total Value', formatter: formatCurrencyWithSymbol },
]

const getSummaryRowClass = (summary: any, index: number) => {
  const isToday = summary.date === new Date().toISOString().split('T')[0]
  return { 'font-weight-bold': index === 0 && isToday }
}

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
    confirmClass: 'btn-warning',
  })

  if (shouldProceed) {
    await recalculate()
  }
}
</script>

<style scoped>
@media (max-width: 666px) {
  :deep(.table) {
    font-size: 12px;
  }

  :deep(.hide-on-mobile) {
    display: none;
  }
}

.list-view .list-item {
  transition: all 0.2s;
  background-color: var(--bs-body-bg);
}

.list-view .list-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
  background-color: var(--bs-light);
}

.list-view .list-item.font-weight-bold {
  border: 2px solid var(--bs-primary);
  background-color: rgba(var(--bs-primary-rgb), 0.05);
}

.list-view small {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
</style>
