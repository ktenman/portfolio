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

import type { RangeExtremes } from '../composables/use-portfolio-chart'
import { formatCurrencyWithSymbol } from '../utils/formatters'

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

const inkColor = rootStyles.getPropertyValue('--color-ink').trim()
const markerFont = rootStyles.getPropertyValue('--font-mono').trim()

export const compactAmount = new Intl.NumberFormat('en-US', {
  notation: 'compact',
  maximumFractionDigits: 1,
})

export const percentAmount = new Intl.NumberFormat('en-US', {
  maximumFractionDigits: 2,
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

const MARKER_RADIUS = 3.5
const TICK_START = 7
const TICK_END = 14
const LABEL_OFFSET = 17
const LABEL_GAP = 13

interface ExtremeMarker {
  x: number
  y: number
  amount: string
  date: string
  direction: 1 | -1
}

interface MarkedDataset {
  seriesId?: string
  extremes?: RangeExtremes | null
}

interface HaloText {
  text: string
  x: number
  y: number
  font: string
  color: string
}

const marked = (dataset: unknown) => dataset as MarkedDataset | undefined

const labelFont = (weight: number) => `${weight} 11px ${markerFont}`

const markerAt = (
  chart: Chart,
  datasetIndex: number,
  index: number,
  direction: 1 | -1
): ExtremeMarker => {
  const point = chart.getDatasetMeta(datasetIndex).data[index]
  return {
    x: point.x,
    y: point.y,
    amount: formatCurrencyWithSymbol(chart.data.datasets[datasetIndex].data[index] as number),
    date: String(chart.data.labels?.[index] ?? ''),
    direction,
  }
}

const drawTick = (ctx: CanvasRenderingContext2D, { x, y, direction }: ExtremeMarker) => {
  ctx.lineWidth = 1
  ctx.strokeStyle = crosshairColor
  ctx.beginPath()
  ctx.moveTo(x, y + direction * TICK_START)
  ctx.lineTo(x, y + direction * TICK_END)
  ctx.stroke()
}

const drawDot = (ctx: CanvasRenderingContext2D, { x, y }: ExtremeMarker, color: string) => {
  ctx.lineWidth = 2
  ctx.fillStyle = surfaceColor
  ctx.strokeStyle = color
  ctx.beginPath()
  ctx.arc(x, y, MARKER_RADIUS, 0, Math.PI * 2)
  ctx.fill()
  ctx.stroke()
}

const drawHaloText = (ctx: CanvasRenderingContext2D, { text, x, y, font, color }: HaloText) => {
  ctx.font = font
  ctx.strokeText(text, x, y)
  ctx.fillStyle = color
  ctx.fillText(text, x, y)
}

const drawLabel = (chart: Chart, marker: ExtremeMarker) => {
  const { ctx, chartArea } = chart
  ctx.font = labelFont(600)
  const amountWidth = ctx.measureText(marker.amount).width
  ctx.font = labelFont(400)
  const width = amountWidth + LABEL_GAP + ctx.measureText(marker.date).width
  const x = Math.min(Math.max(marker.x - width / 2, chartArea.left), chartArea.right - width)
  const y = marker.y + marker.direction * LABEL_OFFSET
  ctx.textBaseline = marker.direction < 0 ? 'bottom' : 'top'
  ctx.lineJoin = 'round'
  ctx.lineWidth = 3
  ctx.strokeStyle = surfaceColor
  drawHaloText(ctx, { text: marker.amount, x, y, font: labelFont(600), color: inkColor })
  const dateX = x + amountWidth + LABEL_GAP
  drawHaloText(ctx, { text: marker.date, x: dateX, y, font: labelFont(400), color: labelColor })
}

export const rangeExtremes: Plugin = {
  id: 'rangeExtremes',
  afterDatasetsDraw(chart) {
    const { datasets } = chart.data
    const datasetIndex = datasets.findIndex(dataset => marked(dataset)?.seriesId === 'totalValue')
    const extremes = marked(datasets[datasetIndex])?.extremes
    if (!extremes || !chart.isDatasetVisible(datasetIndex)) return
    const color = String(datasets[datasetIndex].borderColor)
    const markers = [
      markerAt(chart, datasetIndex, extremes.high, -1),
      markerAt(chart, datasetIndex, extremes.low, 1),
    ]
    chart.ctx.save()
    markers.forEach(marker => {
      drawTick(chart.ctx, marker)
      drawDot(chart.ctx, marker, color)
      drawLabel(chart, marker)
    })
    chart.ctx.restore()
  },
}
