import { describe, it, expect, afterEach, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { TimeRange, type InstrumentDto } from '../../models/generated/domain-models'
import { INSTRUMENTS } from '../../tests/visual/instruments-fixture'
import InstrumentHeatmap from './instrument-heatmap.vue'

vi.mock('@vueuse/core', async importOriginal => {
  const { ref } = await import('vue')
  return {
    ...(await importOriginal<typeof import('@vueuse/core')>()),
    useElementSize: () => ({ width: ref(800), height: ref(600), stop: () => {} }),
  }
})

enableAutoUnmount(afterEach)

const RANGE_CHANGE = { changeAmount: 341.19, changePercent: 1.87 }

const mountHeatmap = (props: Partial<{ instruments: InstrumentDto[]; isLoading: boolean }> = {}) =>
  mount(InstrumentHeatmap, {
    props: {
      instruments: INSTRUMENTS,
      portfolioXirr: 0.1842,
      rangeChange: RANGE_CHANGE,
      period: TimeRange.ONE_DAY,
      isLoading: false,
      isError: false,
      ...props,
    },
  })

type Wrapper = ReturnType<typeof mountHeatmap>

const boxFor = (wrapper: Wrapper, ticker: string) =>
  wrapper
    .findAll('[role="region"] button')
    .find(box => box.attributes('aria-label')?.startsWith(`${ticker},`))!

const readout = (wrapper: Wrapper) => wrapper.find('[data-readout]').text()

const without = (symbol: string) => INSTRUMENTS.filter(instrument => instrument.symbol !== symbol)

describe('InstrumentHeatmap', () => {
  it('shows the totals of the Total row', () =>
    expect(
      mountHeatmap()
        .findAll('[data-total] dd')
        .map(figure => figure.text())
    ).toEqual(['€155,377.72', '+€341.19 / +1.87%', '18.42%']))

  it('previews a box in the readout and returns to the selection after', async () => {
    const wrapper = mountHeatmap()
    await boxFor(wrapper, 'TSTWLD').trigger('click')
    await boxFor(wrapper, 'TSTINF').trigger('pointerenter')
    const previewed = readout(wrapper)

    await boxFor(wrapper, 'TSTINF').trigger('pointerleave')

    expect([previewed.includes('TSTINF'), readout(wrapper).includes('TSTWLD')]).toEqual([
      true,
      true,
    ])
  })

  it('keeps the selection by id through a refetch', async () => {
    const wrapper = mountHeatmap()
    await boxFor(wrapper, 'TSTINF').trigger('click')

    await wrapper.setProps({
      instruments: INSTRUMENTS.map(instrument =>
        instrument.id === 103 ? { ...instrument, currentValue: 20000 } : { ...instrument }
      ),
    })

    expect(readout(wrapper)).toContain('€20,000.00')
  })

  it('clears the selection when the position leaves the map', async () => {
    const wrapper = mountHeatmap()
    await boxFor(wrapper, 'TSTINF').trigger('click')

    await wrapper.setProps({ instruments: without('TSTINF:GER:EUR') })

    expect([readout(wrapper), wrapper.findAll('[aria-pressed="true"]').length]).toEqual([
      expect.stringContaining('Select a box to see its figures.'),
      0,
    ])
  })

  it('keeps the selection while another period loads', async () => {
    const wrapper = mountHeatmap()
    await boxFor(wrapper, 'TSTINF').trigger('click')
    await wrapper.setProps({ instruments: [], isLoading: true })

    await wrapper.setProps({ instruments: INSTRUMENTS, isLoading: false })

    expect(boxFor(wrapper, 'TSTINF').attributes('aria-pressed')).toBe('true')
  })

  it('keeps the preview through a refetch', async () => {
    const wrapper = mountHeatmap()
    await boxFor(wrapper, 'TSTWLD').trigger('pointerenter')

    await wrapper.setProps({ instruments: INSTRUMENTS.map(instrument => ({ ...instrument })) })

    expect(readout(wrapper)).toContain('TSTWLD')
  })

  it('drops the preview while another period loads', async () => {
    const wrapper = mountHeatmap()
    await boxFor(wrapper, 'TSTWLD').trigger('pointerenter')
    await wrapper.setProps({ instruments: [], isLoading: true })

    await wrapper.setProps({ instruments: INSTRUMENTS, isLoading: false })

    expect(readout(wrapper)).toContain('Select a box to see its figures.')
  })

  it('shows a dash for a missing XIRR', async () => {
    const wrapper = mountHeatmap()

    await boxFor(wrapper, 'TSTAIX').trigger('click')

    expect(wrapper.findAll('[data-readout] dd').map(figure => figure.text())).toEqual([
      '€7,315.10',
      '4.71%',
      '—',
    ])
  })

  it('counts positions too small to draw', () =>
    expect(
      mountHeatmap({
        instruments: [
          ...INSTRUMENTS,
          { ...INSTRUMENTS[0], id: 999, symbol: 'TSTÄÄR', currentValue: 77.7 },
        ],
      }).text()
    ).toContain('1 position under 0.1% is too small to draw and appears only in the list.'))

  it('shows the missing key only when a change is missing', () =>
    expect(
      [INSTRUMENTS, without('TSTNDX:NSQ:USD')].map(instruments =>
        mountHeatmap({ instruments }).find('.legend-missing').exists()
      )
    ).toEqual([true, false]))

  it('names the missing key after the period', () =>
    expect(mountHeatmap().find('.legend-missing').text()).toBe('No 1D change'))

  it('explains an empty map and leaves out the list', () => {
    const wrapper = mountHeatmap({ instruments: [] })

    expect([wrapper.text(), wrapper.find('ol').exists()]).toEqual([
      expect.stringContaining('No open positions for the selected platforms.'),
      false,
    ])
  })

  it('keeps the map height while loading', () => {
    const map = mountHeatmap({ instruments: [], isLoading: true }).find('[data-map]')

    expect([map.classes('h-[340px]'), map.find('.skeleton-wrapper').exists()]).toEqual([true, true])
  })
})
