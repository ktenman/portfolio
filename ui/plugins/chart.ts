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
const inkColor = rootStyles.getPropertyValue('--color-ink').trim()

export const gridColor = rootStyles.getPropertyValue('--color-hairline').trim()
export const labelColor = rootStyles.getPropertyValue('--color-ink-soft').trim()
export const surfaceColor = rootStyles.getPropertyValue('--color-surface').trim()

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

const strokeVerticalLine = (
  ctx: CanvasRenderingContext2D,
  x: number,
  from: number,
  to: number,
  dash: number[] = []
) => {
  ctx.lineWidth = 1
  ctx.strokeStyle = crosshairColor
  ctx.setLineDash(dash)
  ctx.beginPath()
  ctx.moveTo(x, from)
  ctx.lineTo(x, to)
  ctx.stroke()
}

export const crosshair: Plugin = {
  id: 'crosshair',
  afterDatasetsDraw(chart) {
    const active = chart.tooltip?.getActiveElements() ?? []
    if (!active.length) return
    const { x } = active[0].element
    const { top, bottom } = chart.chartArea
    const { ctx } = chart
    ctx.save()
    strokeVerticalLine(ctx, x, top, bottom, [2, 4])
    ctx.restore()
  },
}

const ABOVE = -1
const BELOW = 1
type Direction = typeof ABOVE | typeof BELOW

const MARKER_RADIUS = 3.5
const TICK_START = 7
const TICK_END = 14
const LABEL_OFFSET = 17

const amountFont = `600 11px ${Chart.defaults.font.family}`

interface ExtremeMarker {
  x: number
  y: number
  index: number
  amount: string
  direction: Direction
}

interface ExtremesDataset {
  rangeExtremes?: RangeExtremes | null
}

interface HaloText {
  text: string
  x: number
  y: number
  font: string
  color: string
}

const extremesOf = (dataset: unknown) => (dataset as ExtremesDataset | undefined)?.rangeExtremes

const markerAt = (
  chart: Chart,
  datasetIndex: number,
  index: number,
  direction: Direction
): ExtremeMarker => {
  const { x, y } = chart.getDatasetMeta(datasetIndex).data[index]
  return {
    x,
    y,
    index,
    amount: formatCurrencyWithSymbol(chart.data.datasets[datasetIndex].data[index] as number),
    direction,
  }
}

const drawTick = (ctx: CanvasRenderingContext2D, { x, y, direction }: ExtremeMarker) => {
  strokeVerticalLine(ctx, x, y + direction * TICK_START, y + direction * TICK_END)
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
  ctx.lineJoin = 'round'
  ctx.lineWidth = 3
  ctx.strokeStyle = surfaceColor
  ctx.strokeText(text, x, y)
  ctx.fillStyle = color
  ctx.fillText(text, x, y)
}

const drawLabel = (chart: Chart, marker: ExtremeMarker) => {
  const { ctx, chartArea } = chart
  ctx.font = amountFont
  const width = ctx.measureText(marker.amount).width
  const x = Math.min(Math.max(marker.x - width / 2, chartArea.left), chartArea.right - width)
  const y = marker.y + marker.direction * LABEL_OFFSET
  ctx.textAlign = 'left'
  ctx.textBaseline = marker.direction === ABOVE ? 'bottom' : 'top'
  drawHaloText(ctx, { text: marker.amount, x, y, font: amountFont, color: inkColor })
}

export const rangeExtremes: Plugin = {
  id: 'rangeExtremes',
  afterDatasetsDraw(chart) {
    const { ctx } = chart
    const { datasets } = chart.data
    const datasetIndex = datasets.findIndex(
      (dataset, index) => extremesOf(dataset) && chart.isDatasetVisible(index)
    )
    const extremes = extremesOf(datasets[datasetIndex])
    if (!extremes) return
    const color = String(datasets[datasetIndex].borderColor)
    const hovered = chart.tooltip?.getActiveElements()[0]?.index
    const markers = [
      markerAt(chart, datasetIndex, extremes.high, ABOVE),
      markerAt(chart, datasetIndex, extremes.low, BELOW),
    ]
    ctx.save()
    markers.forEach(marker => {
      drawTick(ctx, marker)
      if (marker.index !== hovered) drawDot(ctx, marker, color)
      drawLabel(chart, marker)
    })
    ctx.restore()
  },
}
