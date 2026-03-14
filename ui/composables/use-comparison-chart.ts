import { computed, type Ref } from 'vue'
import { useWindowSize } from '@vueuse/core'
import type { ChartData, ChartOptions } from 'chart.js'
import type { ComparisonResponse } from '../models/generated/domain-models'
import { formatDate } from '../utils/formatters'
import { shortSymbol } from '../utils/instrument-formatters'
import { CHART_COLORS } from '../constants/chart-colors'
import { sampleDataPoints } from './use-portfolio-chart'

export function useComparisonChart(response: Ref<ComparisonResponse | null | undefined>) {
  const { width } = useWindowSize()

  const chartData = computed<ChartData<'line'> | null>(() => {
    if (!response.value || response.value.instruments.length === 0) return null

    const sortedDates = [
      ...new Set(response.value.instruments.flatMap(inst => inst.dataPoints.map(dp => dp.date))),
    ].sort()
    const maxPoints = Math.min(width.value >= 1000 ? 120 : 60, sortedDates.length)
    const sampledDates = sampleDataPoints(sortedDates, maxPoints)

    return {
      labels: sampledDates.map(d => formatDate(d)),
      datasets: response.value.instruments.map((inst, i) => {
        const pointMap = new Map(inst.dataPoints.map(dp => [dp.date, dp.percentageChange]))
        const color = CHART_COLORS[i % CHART_COLORS.length]
        return {
          label: shortSymbol(inst.symbol),
          borderColor: color,
          backgroundColor: color,
          data: sampledDates.map(d => pointMap.get(d) ?? null),
          pointRadius: 0,
          borderWidth: 2,
          spanGaps: true,
          tension: 0.1,
        }
      }),
    }
  })

  const chartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    interaction: {
      mode: 'index',
      intersect: false,
    },
    plugins: {
      legend: {
        display: false,
      },
      tooltip: {
        callbacks: {
          label: ctx =>
            ctx.parsed.y != null ? `${ctx.dataset.label}: ${ctx.parsed.y.toFixed(2)}%` : '',
        },
      },
    },
    scales: {
      x: {
        ticks: {
          maxTicksLimit: 6,
          maxRotation: 0,
        },
        grid: {
          display: false,
        },
      },
      y: {
        title: {
          display: true,
          text: '% Change',
        },
        ticks: {
          callback: value => `${value}%`,
          maxTicksLimit: 8,
        },
        grid: {
          color: ctx => (ctx.tick.value === 0 ? '#6b7280' : '#e5e7eb'),
        },
      },
    },
  }

  return { chartData, chartOptions, colors: CHART_COLORS }
}
