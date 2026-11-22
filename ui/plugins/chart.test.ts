import { afterEach, describe, it, expect, vi } from 'vitest'
import type { Chart } from 'chart.js'
import type { RangeExtremes } from '../composables/use-portfolio-chart'
import { chartTheme, rangeExtremes } from './chart'

interface DrawnText {
  text: string
  x: number
  y: number
}

interface FakeChartOptions {
  visible?: boolean
  profitVisible?: boolean
  extremes?: RangeExtremes | null
  hovered?: number
}

const CHARACTER_WIDTH = 6
const SERIES_COLOR = 'oklch(0.55 0.09 74)'
const PROFIT_COLOR = 'oklch(0.55 0.09 119)'

const fakeContext = (texts: DrawnText[], fills: string[]) => ({
  fillStyle: '',
  save: () => undefined,
  restore: () => undefined,
  beginPath: () => undefined,
  moveTo: () => undefined,
  lineTo: () => undefined,
  stroke: () => undefined,
  arc: () => undefined,
  fill() {
    fills.push(this.fillStyle)
  },
  setLineDash: () => undefined,
  strokeText: () => undefined,
  measureText: (text: string) => ({ width: text.length * CHARACTER_WIDTH }),
  fillText: (text: string, x: number, y: number) => {
    texts.push({ text, x, y })
  },
})

const fakeChart = (
  texts: DrawnText[],
  fills: string[],
  {
    visible = true,
    profitVisible = false,
    extremes = { low: 0, high: 2 },
    hovered,
  }: FakeChartOptions
) =>
  ({
    ctx: fakeContext(texts, fills),
    chartArea: { left: 40, right: 360, top: 28, bottom: 220 },
    data: {
      labels: ['29.12.25', '30.12.25', '31.12.25'],
      datasets: [
        {
          rangeExtremes: extremes,
          borderColor: SERIES_COLOR,
          data: [102060.28, 150000, 178204.06],
        },
        {
          rangeExtremes: { low: 0, high: 2 },
          borderColor: PROFIT_COLOR,
          data: [-412.3, 4000, 5000],
        },
      ],
    },
    isDatasetVisible: (index: number) => (index === 0 ? visible : profitVisible),
    tooltip: {
      getActiveElements: () => (hovered === undefined ? [] : [{ index: hovered }]),
    },
    getDatasetMeta: () => ({
      data: [
        { x: 40, y: 200 },
        { x: 200, y: 150 },
        { x: 360, y: 100 },
      ],
    }),
  }) as unknown as Chart

const draw = (options: FakeChartOptions = {}) => {
  const texts: DrawnText[] = []
  const fills: string[] = []
  rangeExtremes.afterDatasetsDraw?.(fakeChart(texts, fills, options), {}, {}, false)
  return { texts, fills }
}

const PROBE = 'span[hidden]'

const stubComputedStyle = () =>
  vi.spyOn(window, 'getComputedStyle').mockImplementation(element => {
    const { color, colorScheme } = (element as HTMLElement).style
    return { color: element.isConnected ? `${colorScheme} ${color}` : '' } as CSSStyleDeclaration
  })

const loadChartTheme = async () => {
  vi.resetModules()
  const [chart, theme] = await Promise.all([import('./chart'), import('../composables/use-theme')])
  return { chartTheme: chart.chartTheme, theme: theme.useTheme().theme }
}

const colours = (theme: typeof chartTheme.value) => [
  theme.grid,
  theme.label,
  theme.surface,
  theme.brass,
  theme.crosshair,
  theme.ink,
  theme.tooltip.backgroundColor,
  theme.tooltip.titleColor,
  theme.tooltip.bodyColor,
]

const resolved = (scheme: string) =>
  [
    'hairline',
    'ink-soft',
    'surface',
    'brass',
    'ink-faint',
    'ink',
    'gray-700',
    'paper',
    'paper',
  ].map(token => `${scheme} var(--color-${token})`)

describe('chartTheme', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('should read every colour from its token through a probe set to the light scheme', async () => {
    const { chartTheme: loaded, theme } = await loadChartTheme()
    theme.value = 'light'
    stubComputedStyle()
    expect(colours(loaded.value)).toEqual(resolved('light'))
  })

  it('should read every colour again through a probe set to the dark scheme once it goes dark', async () => {
    const { chartTheme: loaded, theme } = await loadChartTheme()
    stubComputedStyle()
    colours(loaded.value)
    theme.value = 'dark'
    expect(colours(loaded.value)).toEqual(resolved('dark'))
  })

  it('should read through a fresh probe after the page body was emptied', async () => {
    const { chartTheme: loaded, theme } = await loadChartTheme()
    stubComputedStyle()
    colours(loaded.value)
    document.body.replaceChildren()
    theme.value = 'dark'
    expect(loaded.value.grid).toBe('dark var(--color-hairline)')
  })

  it('should keep one probe in the page however often the module loads', async () => {
    const first = await loadChartTheme()
    colours(first.chartTheme.value)
    const second = await loadChartTheme()
    second.theme.value = 'dark'
    colours(second.chartTheme.value)
    expect(document.querySelectorAll(PROBE)).toHaveLength(1)
  })
})

describe('rangeExtremes', () => {
  it('should label the high and the low with only their amount', () => {
    expect(draw().texts.map(drawn => drawn.text)).toEqual(['€178,204.06', '€102,060.28'])
  })

  it('should keep labels inside the chart area when the extremes sit on its edges', () => {
    expect(draw().texts.map(drawn => drawn.x)).toEqual([294, 40])
  })

  it('should place the high label above its point and the low label below', () => {
    expect(draw().texts.map(drawn => drawn.y)).toEqual([83, 217])
  })

  it('should leave both extreme dots hollow while nothing is hovered', () => {
    expect(draw().fills).toEqual([chartTheme.value.surface, chartTheme.value.surface])
  })

  it('should leave the hovered extreme dot to the charts own hover point', () => {
    expect(draw({ hovered: 2 }).fills).toEqual([chartTheme.value.surface])
  })

  it('should draw nothing when every series with extremes is hidden', () => {
    expect(draw({ visible: false }).texts).toEqual([])
  })

  it('should draw nothing for a series that carries no extremes', () => {
    expect(draw({ extremes: null }).texts).toEqual([])
  })

  it('should mark total profit when total value is hidden', () => {
    expect(draw({ visible: false, profitVisible: true }).texts.map(drawn => drawn.text)).toEqual([
      '€5,000.00',
      '-€412.30',
    ])
  })

  it('should mark only total value while both are visible', () => {
    expect(draw({ profitVisible: true }).texts.map(drawn => drawn.text)).toEqual([
      '€178,204.06',
      '€102,060.28',
    ])
  })
})
