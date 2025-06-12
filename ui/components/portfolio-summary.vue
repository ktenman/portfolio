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

      <portfolio-table :summaries="reversedSummaries" />

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
import { onMounted, onUnmounted, defineAsyncComponent, computed } from 'vue'
import { usePortfolioSummaryQuery } from '../composables/use-portfolio-summary-query'
import { usePortfolioChart } from '../composables/use-portfolio-chart'
import { useConfirm } from '../composables/use-confirm'
import PortfolioActions from './portfolio/portfolio-actions.vue'
import PortfolioTable from './portfolio/portfolio-table.vue'
import ConfirmDialog from './shared/confirm-dialog.vue'

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

const showRecalculationMessage = computed(
  () => recalculationMessage.value && viewState.value !== 'ERROR'
)

const handleScroll = async () => {
  if (isFetching.value) return
  if (!hasMoreData?.value) return

  const scrolledToBottom = window.innerHeight + window.scrollY >= document.body.offsetHeight - 100
  if (!scrolledToBottom) return

  await fetchSummaries()
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})

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
