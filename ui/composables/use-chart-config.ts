import { ChartOptions } from 'chart.js'

export interface ChartColors {
  primary: string
  secondary: string
  success: string
  danger: string
  warning: string
  info: string
}

export const chartColors: ChartColors = {
  primary: '#8884d8',
  secondary: '#82ca9d',
  success: '#28a745',
  danger: '#dc3545',
  warning: '#ffc658',
  info: '#17a2b8',
}

export const useChartConfig = () => {
  const getBaseChartOptions = (overrides: Partial<ChartOptions> = {}): ChartOptions => {
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: {
        mode: 'index',
        intersect: false,
      },
      plugins: {
        legend: {
          display: true,
          position: 'top' as const,
        },
        tooltip: {
          enabled: true,
          mode: 'index',
          intersect: false,
        },
      },
      scales: {
        x: {
          grid: {
            display: false,
          },
          ticks: {
            maxTicksLimit: 8,
          },
        },
        y: {
          grid: {
            color: 'rgba(0, 0, 0, 0.05)',
          },
          ticks: {
            maxTicksLimit: 8,
          },
        },
      },
      ...overrides,
    } as ChartOptions
  }

  const getLineChartOptions = (title?: string, yAxisLabel?: string): ChartOptions<'line'> => {
    return {
      ...getBaseChartOptions(),
      plugins: {
        ...getBaseChartOptions().plugins,
        title: title ? { display: true, text: title, font: { size: 16 } } : undefined,
      },
      scales: {
        ...getBaseChartOptions().scales,
        y: {
          ...getBaseChartOptions().scales?.y,
          title: yAxisLabel ? { display: true, text: yAxisLabel } : undefined,
        },
      },
    } as ChartOptions<'line'>
  }

  const getBarChartOptions = (title?: string, yAxisLabel?: string): ChartOptions<'bar'> => {
    return {
      ...getBaseChartOptions(),
      plugins: {
        ...getBaseChartOptions().plugins,
        title: title ? { display: true, text: title, font: { size: 16 } } : undefined,
      },
      scales: {
        ...getBaseChartOptions().scales,
        y: {
          ...getBaseChartOptions().scales?.y,
          title: yAxisLabel ? { display: true, text: yAxisLabel } : undefined,
        },
      },
    } as ChartOptions<'bar'>
  }

  const getDualAxisChartOptions = (
    leftAxisLabel: string,
    rightAxisLabel: string
  ): ChartOptions<'line'> => {
    return {
      ...getBaseChartOptions(),
      scales: {
        x: {
          ...getBaseChartOptions().scales?.x,
        },
        y: {
          type: 'linear' as const,
          display: true,
          position: 'left' as const,
          title: {
            display: true,
            text: leftAxisLabel,
          },
          ticks: {
            maxTicksLimit: 8,
          },
        },
        y1: {
          type: 'linear' as const,
          display: true,
          position: 'right' as const,
          title: {
            display: true,
            text: rightAxisLabel,
          },
          grid: {
            drawOnChartArea: false,
          },
          ticks: {
            maxTicksLimit: 8,
          },
        },
      },
    } as ChartOptions<'line'>
  }

  const sampleData = <T>(data: T[], maxPoints: number): T[] => {
    if (data.length <= maxPoints) return data

    const step = Math.ceil(data.length / maxPoints)
    return Array.from({ length: maxPoints }, (_, i) => i * step)
      .filter(i => i < data.length)
      .map(i => data[i])
  }

  return {
    chartColors,
    getBaseChartOptions,
    getLineChartOptions,
    getBarChartOptions,
    getDualAxisChartOptions,
    sampleData,
  }
}
