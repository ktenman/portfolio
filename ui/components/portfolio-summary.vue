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

      <portfolio-chart :data="processedChartData" />

      <data-table
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

    <confirm-dialog
      v-model="isConfirmOpen"
      :title="confirmOptions.title"
      :message="confirmOptions.message"
      :confirm-text="confirmOptions.confirmText"
      :cancel-text="confirmOptions.cancelText"
      :confirm-class="confirmOptions.confirmClass"
      @confirm="handleConfirm"
      @cancel="handleCancel"
    />
  </div>
</template>

<script lang="ts" setup>
import { defineAsyncComponent, computed } from 'vue'
import { useInfiniteScroll } from '@vueuse/core'
import { usePortfolioSummaryQuery } from '../composables/use-portfolio-summary-query'
import { usePortfolioChart } from '../composables/use-portfolio-chart'
import { useConfirm } from '../composables/use-confirm'
import PortfolioActions from './portfolio/portfolio-actions.vue'
import DataTable, { type ColumnDefinition } from './shared/data-table.vue'
import ConfirmDialog from './shared/confirm-dialog.vue'
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

const { isConfirmOpen, confirmOptions, confirm, handleConfirm, handleCancel } = useConfirm()

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
@media (max-width: 767px) {
  :deep(.table) {
    font-size: 12px;
  }

  :deep(.hide-on-mobile) {
    display: none;
  }
}
</style>
