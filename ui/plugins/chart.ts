import {
  Chart,
  type Plugin,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Title,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js'

Chart.register(
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Title,
  Tooltip,
  Legend,
  Filler
)

Chart.defaults.font.family =
  getComputedStyle(document.body).fontFamily || Chart.defaults.font.family

const rootStyles = getComputedStyle(document.documentElement)
const crosshairColor = rootStyles.getPropertyValue('--color-ink-faint').trim()

export const gridColor = rootStyles.getPropertyValue('--color-hairline').trim()
export const labelColor = rootStyles.getPropertyValue('--color-ink-soft').trim()
export const surfaceColor = rootStyles.getPropertyValue('--color-surface').trim()

export const compactAmount = new Intl.NumberFormat('en-US', {
  notation: 'compact',
  maximumFractionDigits: 1,
})

export const tooltipStyle = {
  backgroundColor: rootStyles.getPropertyValue('--color-gray-700').trim(),
  titleColor: rootStyles.getPropertyValue('--color-paper').trim(),
  bodyColor: rootStyles.getPropertyValue('--color-paper').trim(),
  titleFont: { size: 11, weight: 500 },
  titleMarginBottom: 8,
  bodyFont: { size: 12 },
  bodySpacing: 6,
  padding: 12,
  cornerRadius: 8,
  caretSize: 5,
  boxPadding: 6,
  usePointStyle: true,
} as const

export const crosshair: Plugin = {
  id: 'crosshair',
  afterDatasetsDraw(chart) {
    const active = chart.tooltip?.getActiveElements() ?? []
    if (!active.length) return
    const { x } = active[0].element
    const { top, bottom } = chart.chartArea
    const { ctx } = chart
    ctx.save()
    ctx.beginPath()
    ctx.setLineDash([2, 4])
    ctx.lineWidth = 1
    ctx.strokeStyle = crosshairColor
    ctx.moveTo(x, top)
    ctx.lineTo(x, bottom)
    ctx.stroke()
    ctx.restore()
  },
}
