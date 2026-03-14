import { computed, type Ref } from 'vue'
import type { ChartData, ChartOptions, TooltipItem } from 'chart.js'
import type { HorizonPredictionDto } from '../models/generated/domain-models'
import { formatCurrencyWithSymbol, formatCompactCurrency } from '../utils/formatters'

export function usePredictionChart(
  predictions: Ref<HorizonPredictionDto[]>,
  currentValue: Ref<number>
) {
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
          label: (ctx: TooltipItem<'line'>) =>
            `${ctx.dataset.label}: ${formatCurrencyWithSymbol(ctx.parsed.y)}`,
        },
      },
    },
    scales: {
      y: {
        ticks: {
          maxTicksLimit: 6,
          callback: (value: string | number) => formatCompactCurrency(Number(value)),
        },
        grid: { color: 'rgba(0, 0, 0, 0.05)' },
      },
      x: {
        grid: { display: false },
      },
    },
  }))

  return { chartData, chartOptions }
}
