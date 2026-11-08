import type { Chart, LineElement, Plugin } from 'chart.js'
import { withAlpha } from '../constants/chart-colors'
import { readFrame, xOfDate, type ChartFrame } from '../services/chart-frame'
import type { ChartPeriod } from '../services/chart-period'
import { brassColor, drawDot, surfaceColor } from './chart'

export interface PeriodDrawing {
  period: ChartPeriod | null
  dragging: boolean
  inside: boolean
}

interface PeriodVeilOptions {
  drawing: () => PeriodDrawing
  dates: () => string[]
  onFrame: (frame: ChartFrame) => void
}

const VEIL_ALPHA = 0.74
const TINT_ALPHA = 0.05

const lineY = (chart: Chart<'line'>, x: number): number | null => {
  const index = chart.data.datasets.findIndex((_, at) => chart.isDatasetVisible(at))
  if (index < 0) return null
  const line = chart.getDatasetMeta(index).dataset as LineElement | undefined
  const point = line?.interpolate({ x, y: 0 }, 'x')
  const first = Array.isArray(point) ? point[0] : point
  return first?.y ?? null
}

const fillPlot = (ctx: CanvasRenderingContext2D, frame: ChartFrame, from: number, to: number) => {
  ctx.fillRect(from, frame.top, to - from, frame.bottom - frame.top)
}

const strokeEdge = (ctx: CanvasRenderingContext2D, frame: ChartFrame, x: number) => {
  const crisp = Math.round(x) + 0.5
  ctx.beginPath()
  ctx.moveTo(crisp, frame.top)
  ctx.lineTo(crisp, frame.bottom)
  ctx.stroke()
}

const drawPeriod = (chart: Chart<'line'>, frame: ChartFrame, drawing: PeriodDrawing) => {
  if (!drawing.period) return
  const { ctx } = chart
  const edges = [xOfDate(frame, drawing.period.from), xOfDate(frame, drawing.period.to)]
  const [from, to] = edges
  ctx.save()
  ctx.fillStyle = withAlpha(surfaceColor, VEIL_ALPHA)
  fillPlot(ctx, frame, frame.left, from)
  fillPlot(ctx, frame, to, frame.right)
  if (drawing.inside) {
    ctx.fillStyle = withAlpha(brassColor, TINT_ALPHA)
    fillPlot(ctx, frame, from, to)
  }
  ctx.strokeStyle = brassColor
  ctx.lineWidth = 1
  edges.forEach(x => strokeEdge(ctx, frame, x))
  edges.forEach(x => {
    const y = lineY(chart, x)
    if (y !== null) drawDot(ctx, { x, y }, brassColor)
  })
  ctx.restore()
}

const dropHover = (chart: Chart<'line'>) => {
  chart.setActiveElements([])
  chart.tooltip?.setActiveElements([], { x: 0, y: 0 })
}

export function createPeriodVeil({ drawing, dates, onFrame }: PeriodVeilOptions): Plugin<'line'> {
  let reported = ''
  return {
    id: 'periodVeil',
    beforeEvent: () => (drawing().dragging ? false : undefined),
    beforeDraw(chart) {
      if (drawing().dragging) dropHover(chart)
    },
    afterDatasetsDraw(chart) {
      const frame = readFrame(chart, dates())
      if (frame) drawPeriod(chart, frame, drawing())
    },
    afterRender(chart) {
      const frame = readFrame(chart, dates())
      const key = JSON.stringify(frame)
      if (!frame || key === reported) return
      reported = key
      onFrame(frame)
    },
  }
}
