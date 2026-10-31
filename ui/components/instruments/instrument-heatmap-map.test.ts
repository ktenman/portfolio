import { describe, it, expect, afterEach, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { TimeRange } from '../../models/generated/domain-models'
import { drawablePositions, tickerOf } from '../../services/instrument-heatmap'
import { INSTRUMENTS } from '../../tests/visual/instruments-fixture'
import InstrumentHeatmapMap from './instrument-heatmap-map.vue'

vi.mock('@vueuse/core', async importOriginal => {
  const { ref } = await import('vue')
  return {
    ...(await importOriginal<typeof import('@vueuse/core')>()),
    useElementSize: () => ({ width: ref(800), height: ref(600), stop: () => {} }),
  }
})

enableAutoUnmount(afterEach)

const POSITIONS = drawablePositions(INSTRUMENTS)
const TOTAL = INSTRUMENTS.reduce((sum, { currentValue }) => sum + (currentValue ?? 0), 0)

const mountMap = (selectedId: number | null = null) =>
  mount(InstrumentHeatmapMap, {
    props: {
      positions: POSITIONS,
      total: TOTAL,
      period: TimeRange.ONE_DAY,
      selectedId,
      wide: true,
    },
  })

const boxIn = (wrapper: ReturnType<typeof mountMap>, ticker: string) =>
  wrapper.findAll('button').find(box => box.attributes('aria-label')?.startsWith(`${ticker},`))!

const boxFor = (ticker: string) => boxIn(mountMap(), ticker)

describe('InstrumentHeatmapMap', () => {
  it('labels a box with its value, weight and change', () =>
    expect(boxFor('TSTINF').attributes('aria-label')).toBe(
      'TSTINF, Test S&P 500 Information Technology Sector. Value €18,402.42, 11.84% of the portfolio. 1D change +1.17%, +€212.66.'
    ))

  it('labels a box without a change', () => {
    const box = boxFor('TSTNDX')

    expect([box.attributes('aria-label')?.endsWith('No 1D change.'), box.text()]).toEqual([
      true,
      'TSTNDX—',
    ])
  })

  it('colours a box by its step for the period', () =>
    expect(
      [
        ['TSTINF', 'bg-gain-step-3'],
        ['TSTBTC', 'bg-loss-step-3'],
        ['TSTEUR', 'bg-change-flat'],
        ['TSTNDX', 'border-dashed'],
      ].map(([ticker, fill]) => boxFor(ticker).classes(fill))
    ).toEqual([true, true, true, true]))

  it('previews a box on hover and on focus', async () => {
    const wrapper = mountMap()
    const box = boxIn(wrapper, 'TSTINF')

    await box.trigger('pointerenter')
    await box.trigger('pointerleave')
    await box.trigger('focus')
    await box.trigger('blur')

    expect(wrapper.emitted('preview')).toEqual([[103], [null], [103], [null]])
  })

  it('selects a box with a click', async () => {
    const wrapper = mountMap()

    await boxIn(wrapper, 'TSTINF').trigger('click')

    expect(wrapper.emitted('select')).toEqual([[103]])
  })

  it('makes every box a native button for Enter and Space', () =>
    expect(
      mountMap()
        .findAll('button')
        .every(box => box.attributes('type') === 'button')
    ).toBe(true))

  it('clears the selection on a second click', async () => {
    const wrapper = mountMap(103)

    await boxIn(wrapper, 'TSTINF').trigger('click')

    expect(wrapper.emitted('select')).toEqual([[null]])
  })

  it('clears the selection with Escape', async () => {
    const wrapper = mountMap(103)

    await wrapper.find('[role="region"]').trigger('keydown', { key: 'Escape' })

    expect(wrapper.emitted('select')).toEqual([[null]])
  })

  it('marks only the selected box as pressed', () =>
    expect(
      mountMap(103)
        .findAll('button[aria-pressed="true"]')
        .map(box => box.attributes('aria-label')?.split(',')[0])
    ).toEqual(['TSTINF']))

  it('orders the boxes by value', () =>
    expect(
      mountMap()
        .findAll('button')
        .map(box => box.attributes('aria-label')?.split(',')[0])
    ).toEqual(POSITIONS.map(({ symbol }) => tickerOf(symbol))))

  it('names the map region', () =>
    expect(mountMap().find('[role="region"]').attributes('aria-label')).toBe(
      'Heatmap of positions'
    ))
})
