import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { TimeRange } from '../../models/generated/domain-models'
import { INSTRUMENTS } from '../../tests/visual/instruments-fixture'
import InstrumentChangeList from './instrument-change-list.vue'

enableAutoUnmount(afterEach)

const mountList = (interactive: boolean) =>
  mount(InstrumentChangeList, {
    props: { positions: INSTRUMENTS, period: TimeRange.ONE_WEEK, interactive },
  })

const rowFor = (wrapper: ReturnType<typeof mountList>, ticker: string) =>
  wrapper.findAll('li').find(row => row.text().startsWith(ticker))!

describe('InstrumentChangeList', () => {
  it('heads the list with the period', () =>
    expect(mountList(false).find('h2').text()).toBe('1W change in euros'))

  it('lists every open position by change with unknown changes last', () => {
    const rows = mountList(false).findAll('li')

    expect([rows.length, rows[0].text(), rows[rows.length - 1].text()]).toEqual([
      INSTRUMENTS.length,
      'TSTINF+€212.66',
      'TSTNDX—',
    ])
  })

  it('sizes a bar by its share of the largest change', () =>
    expect(rowFor(mountList(false), 'TSTORS').find('[data-bar]').attributes('style')).toContain(
      `--share: ${84.19 / 212.66}`
    ))

  it('hides the bars from assistive technology', () =>
    expect(
      mountList(false)
        .findAll('li')
        .every(row => row.find('[data-bar]').element.closest('[aria-hidden="true"]') !== null)
    ).toBe(true))

  it('previews and selects a row in the two-column layout', async () => {
    const wrapper = mountList(true)
    const row = rowFor(wrapper, 'TSTINF')

    await row.trigger('pointerenter')
    await row.trigger('pointerleave')
    await row.trigger('click')

    expect([wrapper.emitted('preview'), wrapper.emitted('select')]).toEqual([
      [[103], [null]],
      [[103]],
    ])
  })

  it('dont make rows Tab stops', () =>
    expect(
      mountList(true)
        .findAll('li')
        .some(row => row.attributes('tabindex') !== undefined)
    ).toBe(false))

  it('ignores pointer events outside the two-column layout', async () => {
    const wrapper = mountList(false)
    const row = rowFor(wrapper, 'TSTINF')

    await row.trigger('pointerenter')
    await row.trigger('click')

    expect([wrapper.emitted('preview'), wrapper.emitted('select')]).toEqual([undefined, undefined])
  })
})
