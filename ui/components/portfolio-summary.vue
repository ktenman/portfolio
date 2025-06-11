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
  </div>
</template>

<script lang="ts" setup>
import { usePortfolioSummary } from '../composables/use-portfolio-summary'
import PortfolioActions from './portfolio/portfolio-actions.vue'
import PortfolioChart from './portfolio/portfolio-chart.vue'
import PortfolioTable from './portfolio/portfolio-table.vue'

const {
  summaries,
  reversedSummaries,
  isLoading,
  isRecalculating,
  isFetching,
  error,
  recalculationMessage,
  processedChartData,
  recalculate,
} = usePortfolioSummary()

const handleRecalculate = async () => {
  if (
    !confirm(
      'This will delete all current summary data and recalculate it from scratch. This operation may take some time. Continue?'
    )
  ) {
    return
  }

  await recalculate()
}
</script>
