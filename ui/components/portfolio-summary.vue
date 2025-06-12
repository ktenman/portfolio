<template>
  <div class="container mt-2">
    <portfolio-actions
      :is-loading="isLoading"
      :is-recalculating="isRecalculating"
      @recalculate="handleRecalculate"
    />

    <div v-if="isLoading" class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>

    <div v-if="error && !isLoading" class="alert alert-danger" role="alert">
      {{ error }}
    </div>

    <div
      v-if="recalculationMessage && !error"
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

    <div v-if="!isLoading && !error">
      <div v-if="summaries.length === 0" class="alert alert-info" role="alert">
        No portfolio summary data found.
      </div>

      <div v-else>
        <portfolio-chart :data="processedChartData" />

        <portfolio-table :summaries="reversedSummaries" />

        <div v-if="isFetching" class="text-center mt-3">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading more data...</span>
          </div>
        </div>
      </div>
    </div>

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
import { onMounted, onUnmounted } from 'vue'
import { usePortfolioSummary } from '../composables/use-portfolio-summary'
import { usePortfolioChart } from '../composables/use-portfolio-chart'
import { useConfirm } from '../composables/use-confirm'
import PortfolioActions from './portfolio/portfolio-actions.vue'
import PortfolioChart from './portfolio/portfolio-chart.vue'
import PortfolioTable from './portfolio/portfolio-table.vue'
import ConfirmDialog from './shared/confirm-dialog.vue'

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
  fetchInitialData,
} = usePortfolioSummary()

const { processedChartData } = usePortfolioChart(summaries)

const { isConfirmOpen, confirmOptions, confirm, handleConfirm, handleCancel } = useConfirm()

const handleScroll = async () => {
  if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 100) {
    await fetchSummaries()
  }
}

onMounted(() => {
  fetchInitialData()
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
