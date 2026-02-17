<template>
  <div class="card mb-3">
    <div class="card-header d-flex justify-content-between align-items-center py-2">
      <h6 class="mb-0">Return Predictions</h6>
      <small v-if="hasSufficientData" class="text-muted">
        Based on {{ dataPointCount }} days of data
      </small>
    </div>
    <div class="card-body p-2">
      <div v-if="isLoading">
        <skeleton-loader type="card" />
      </div>
      <div v-else-if="error" class="alert alert-danger mb-0">
        Failed to load predictions. Please try again later.
      </div>
      <div v-else-if="!hasSufficientData" class="alert alert-info mb-0">
        Insufficient data for predictions. At least 30 days of portfolio history required ({{
          dataPointCount
        }}
        days available).
      </div>
      <div v-else class="row g-2">
        <div v-for="prediction in predictions" :key="prediction.horizon" class="col-6 col-lg-3">
          <div class="card h-100 border">
            <div class="card-body text-center p-2">
              <h6 class="card-title text-muted mb-2">
                {{ formatHorizonLabel(prediction.horizon) }}
              </h6>
              <div class="mb-1">
                <div class="fw-bold">{{ formatCurrencyWithSymbol(prediction.expectedValue) }}</div>
                <small class="text-muted">Expected</small>
              </div>
              <div class="d-flex justify-content-between mt-2">
                <div>
                  <div class="text-success small">
                    {{ formatCurrencyWithSymbol(prediction.optimisticValue) }}
                  </div>
                  <small class="text-muted">Best</small>
                </div>
                <div>
                  <div class="text-danger small">
                    {{ formatCurrencyWithSymbol(prediction.pessimisticValue) }}
                  </div>
                  <small class="text-muted">Worst</small>
                </div>
              </div>
              <div class="mt-2 border-top pt-2">
                <small class="text-muted">
                  XIRR: {{ formatCurrencyWithSymbol(prediction.xirrProjectedValue) }}
                </small>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { useReturnPredictions } from '../../composables/use-return-predictions'
import { formatCurrencyWithSymbol } from '../../utils/formatters'
import SkeletonLoader from '../shared/skeleton-loader.vue'

const { predictions, hasSufficientData, dataPointCount, isLoading, error } = useReturnPredictions()

const HORIZON_LABELS: Record<string, string> = {
  '1M': '1 Month',
  '3M': '3 Months',
  '6M': '6 Months',
  '1Y': '1 Year',
}

const formatHorizonLabel = (horizon: string): string => HORIZON_LABELS[horizon] ?? horizon
</script>
