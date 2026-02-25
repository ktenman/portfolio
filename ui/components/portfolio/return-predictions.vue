<template>
  <div class="card mb-3">
    <div class="card-header d-flex justify-content-between align-items-center py-2">
      <h6 class="mb-0">Return Predictions</h6>
      <div v-if="hasSufficientData" class="d-flex align-items-center gap-2">
        <small class="text-muted">Based on {{ dataPointCount }} days</small>
        <div class="input-group input-group-sm" style="width: 160px">
          <span class="input-group-text">€/mo</span>
          <input
            v-model.number="monthlyInput"
            type="number"
            class="form-control form-control-sm"
            min="0"
            step="100"
            placeholder="Auto"
          />
        </div>
      </div>
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
      <div v-else>
        <div class="row g-2">
          <div v-for="prediction in predictions" :key="prediction.horizon" class="col-6 col-lg">
            <div class="card h-100 prediction-card">
              <div class="card-body text-center p-2">
                <h6 class="card-title text-muted mb-2">
                  {{ formatHorizonLabel(prediction.horizon) }}
                </h6>
                <div class="mb-2">
                  <div class="fs-5 fw-bold">
                    {{ formatCurrencyWithSymbol(prediction.expectedValue) }}
                  </div>
                  <small :class="changeTextClass(prediction.expectedValue)">
                    {{ formatChange(prediction.expectedValue) }}
                  </small>
                </div>
                <div class="d-flex justify-content-between border-top pt-2">
                  <div>
                    <div class="text-success small fw-semibold">
                      {{ formatCurrencyWithSymbol(prediction.optimisticValue) }}
                    </div>
                    <small class="text-muted">Best</small>
                  </div>
                  <div>
                    <div class="text-danger small fw-semibold">
                      {{ formatCurrencyWithSymbol(prediction.pessimisticValue) }}
                    </div>
                    <small class="text-muted">Worst</small>
                  </div>
                </div>
                <div v-if="prediction.contributions > 0" class="border-top pt-1 mt-2">
                  <small class="text-muted">
                    incl. {{ formatCurrencyWithSymbol(prediction.contributions) }} invested
                  </small>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="mt-3 prediction-chart-container">
          <Line :data="chartData" :options="chartOptions" />
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { Line } from 'vue-chartjs'
import type { ChartData, ChartOptions } from 'chart.js'
import { useReturnPredictions } from '../../composables/use-return-predictions'
import { formatCurrencyWithSymbol } from '../../utils/formatters'
import SkeletonLoader from '../shared/skeleton-loader.vue'
import '../../plugins/chart'

const monthlyInput = useLocalStorage<number | undefined>(
  'portfolio_monthly_contribution',
  undefined
)
const customContribution = computed(() =>
  monthlyInput.value !== undefined && monthlyInput.value >= 0 ? monthlyInput.value : undefined
)

const { predictions, hasSufficientData, dataPointCount, currentValue, isLoading, error } =
  useReturnPredictions(customContribution)

const HORIZON_LABELS: Record<string, string> = {
  '1M': '1 Month',
  '3M': '3 Months',
  '6M': '6 Months',
  '1Y': '1 Year',
  '2Y': '2 Years',
}

const formatHorizonLabel = (horizon: string): string => HORIZON_LABELS[horizon] ?? horizon

const formatChange = (value: number): string => {
  const cv = currentValue.value
  if (cv === 0) return '+0.0%'
  const pct = ((value - cv) / cv) * 100
  const sign = pct >= 0 ? '+' : ''
  return `${sign}${pct.toFixed(1)}%`
}

const changeTextClass = (value: number): string => {
  const cv = currentValue.value
  return value >= cv ? 'text-success' : 'text-danger'
}

const formatCompactCurrency = (value: number): string => {
  if (value >= 1_000_000) return `€${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `€${(value / 1_000).toFixed(0)}k`
  return `€${value.toFixed(0)}`
}

const chartData = computed<ChartData<'line'>>(() => {
  const labels = ['Now', ...predictions.value.map(p => p.horizon)]
  const cv = currentValue.value
  return {
    labels,
    datasets: [
      {
        label: 'Optimistic',
        data: [cv, ...predictions.value.map(p => p.optimisticValue)],
        borderColor: '#198754',
        borderDash: [5, 5],
        borderWidth: 1.5,
        pointRadius: 3,
        pointStyle: 'circle' as const,
        tension: 0.3,
        fill: 2,
        backgroundColor: 'rgba(13, 110, 253, 0.06)',
      },
      {
        label: 'Expected',
        data: [cv, ...predictions.value.map(p => p.expectedValue)],
        borderColor: '#0d6efd',
        borderWidth: 2.5,
        pointRadius: 4,
        pointStyle: 'circle' as const,
        tension: 0.3,
        fill: false,
      },
      {
        label: 'Pessimistic',
        data: [cv, ...predictions.value.map(p => p.pessimisticValue)],
        borderColor: '#dc3545',
        borderDash: [5, 5],
        borderWidth: 1.5,
        pointRadius: 3,
        pointStyle: 'circle' as const,
        tension: 0.3,
        fill: false,
      },
    ],
  }
})

const chartOptions = computed<ChartOptions<'line'>>(() => ({
  responsive: true,
  maintainAspectRatio: false,
  animation: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: {
    legend: {
      position: 'bottom' as const,
      labels: {
        usePointStyle: true,
        pointStyleWidth: 10,
        padding: 16,
      },
    },
    filler: { propagate: false },
    tooltip: {
      callbacks: {
        label: (ctx: any) => `${ctx.dataset.label}: ${formatCurrencyWithSymbol(ctx.parsed.y)}`,
      },
    },
  },
  scales: {
    y: {
      ticks: {
        maxTicksLimit: 6,
        callback: (value: any) => formatCompactCurrency(Number(value)),
      },
      grid: { color: 'rgba(0, 0, 0, 0.05)' },
    },
    x: {
      grid: { display: false },
    },
  },
}))
</script>

<style lang="scss" scoped>
.prediction-card {
  border: 1px solid rgba(0, 0, 0, 0.1);
  transition: box-shadow 0.15s ease-in-out;

  &:hover {
    box-shadow: 0 0.25rem 0.5rem rgba(0, 0, 0, 0.08);
  }
}

.prediction-chart-container {
  height: 22rem;
}
</style>
