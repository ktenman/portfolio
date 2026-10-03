import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import FundReportPopover from './fund-report-popover.vue'
import type { FundReportDto } from '../../models/generated/domain-models'

const REPORTS: FundReportDto[] = [
  {
    asOfDate: '2026-08-31',
    funds: [
      { isin: 'IE000I9HGDZ3', name: 'Xtrackers MSCI World Screened', weight: 58.26 },
      { isin: 'IE00BKPTWY98', name: 'iShares Emerging Market Screened', weight: 41.62 },
    ],
  },
  {
    asOfDate: '2026-07-31',
    funds: [
      { isin: 'IE0009FT4LX4', name: 'CCF Developed World', weight: 88.2 },
      { isin: 'IE00BKPTWY98', name: 'iShares Emerging Market Screened', weight: 11.61 },
    ],
  },
]

const mountPopover = () =>
  mount(FundReportPopover, { props: { name: 'Tuleva Täiendav Pensionifond', reports: REPORTS } })

const fundNames = (wrapper: ReturnType<typeof mountPopover>) =>
  wrapper.findAll('.fund-table .fund-name').map(cell => cell.text())

describe('FundReportPopover', () => {
  it('points the report button at the popover', () => {
    const wrapper = mountPopover()

    const target = wrapper.find('.report-btn').attributes('popovertarget')

    expect(wrapper.find(`#${target}`).attributes()).toMatchObject({
      class: 'report-popover',
      popover: '',
    })
  })

  it('hides the popover from its close button', () => {
    const wrapper = mountPopover()

    const close = wrapper.find('.report-popover .btn-close')

    expect([close.attributes('popovertarget'), close.attributes('popovertargetaction')]).toEqual([
      wrapper.find('.report-popover').attributes('id'),
      'hide',
    ])
  })

  it('heads the popover with the fund name and its ISIN', () => {
    const wrapper = mountPopover()

    expect([wrapper.find('.report-title').text(), wrapper.find('.report-subtitle').text()]).toEqual(
      ['Tuleva Täiendav Pensionifond', 'EE3600001707 · Monthly report']
    )
  })

  it('labels the month picker', () => {
    const wrapper = mountPopover()

    const label = wrapper.find('.report-label')

    expect(wrapper.find(`#${label.attributes('for')}`).classes()).toContain('report-select')
  })

  it('offers every report month, newest first', () => {
    const wrapper = mountPopover()

    expect(wrapper.findAll('.report-select option').map(option => option.text())).toEqual([
      '31.08.2026',
      '31.07.2026',
    ])
  })

  it('lists cash before the funds dropped since the previous report', () => {
    const wrapper = mountPopover()

    expect(fundNames(wrapper)).toEqual([
      'Xtrackers MSCI World Screened',
      'iShares Emerging Market Screened',
      'Cash',
      'CCF Developed World',
    ])
  })

  it('marks the funds that are new since the previous report', () => {
    const wrapper = mountPopover()

    expect(wrapper.findAll('.fund-table tr.new .fund-name').map(cell => cell.text())).toEqual([
      'Xtrackers MSCI World Screened',
    ])
  })

  it('shows every weight but that of a dropped fund', () => {
    const wrapper = mountPopover()

    expect(wrapper.findAll('.fund-table td.num').map(cell => cell.text())).toEqual([
      '58.26%',
      '41.62%',
      '0.12%',
      '—',
    ])
  })

  it('draws a stack segment for every fund weight and cash', () => {
    const wrapper = mountPopover()

    const segments = wrapper.findAll('.fund-stack span')

    expect(
      segments.map(segment => Number((segment.element as HTMLElement).style.flexGrow).toFixed(2))
    ).toEqual(['58.26', '41.62', '0.12'])
  })

  it('lists the funds of the month picked', async () => {
    const wrapper = mountPopover()

    await wrapper.find('.report-select').setValue('1')

    expect(fundNames(wrapper)).toEqual([
      'CCF Developed World',
      'iShares Emerging Market Screened',
      'Cash',
    ])
  })

  it('links the PDF of the month picked', async () => {
    const wrapper = mountPopover()

    await wrapper.find('.report-select').setValue('1')

    expect(wrapper.find('.report-link').attributes('href')).toBe(
      '/api/funds/EE3600001707/reports/2026-07-31'
    )
  })
})
