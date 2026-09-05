import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import EtfBreakdown from './etf-breakdown.vue'
import EtfBreakdownStats from './etf-breakdown-stats.vue'
import EtfBreakdownTable from './etf-breakdown-table.vue'
import EtfBreakdownChart from './etf-breakdown-chart.vue'
import { etfBreakdownService, instrumentsService } from '../../services/api'
import { Currency } from '../../models/generated/domain-models'
import type { EtfHoldingBreakdownDto, InstrumentDto } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  etfBreakdownService: {
    getBreakdown: vi.fn(),
  },
  instrumentsService: {
    getAll: vi.fn(),
    refreshPrices: vi.fn().mockResolvedValue({ status: 'ok' }),
  },
  logoService: {
    prefetchCandidates: vi.fn().mockResolvedValue(undefined),
    getLogoUrl: (uuid: string) => `/api/logos/${uuid}`,
  },
}))

describe('etf-breakdown', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  const mockHoldings: EtfHoldingBreakdownDto[] = [
    {
      holdingUuid: 'uuid-1',
      holdingTicker: 'AAPL',
      holdingName: 'Apple Inc.',
      percentageOfTotal: 25,
      totalValueEur: 10000,
      holdingSector: 'Technology',
      holdingIndustry: 'Technology Hardware, Storage & Peripherals',
      holdingGicsSector: 'Information Technology',
      holdingCountryCode: 'US',
      holdingCountryName: 'United States',
      inEtfs: 'VWCE:XETRA',
      numEtfs: 1,
      platforms: 'LIGHTYEAR',
    },
  ]

  const mockInstrument: InstrumentDto = {
    id: 1,
    symbol: 'VWCE:XETRA',
    name: 'Vanguard FTSE All-World',
    category: 'ETF',
    baseCurrency: 'EUR',
    fundCurrency: Currency.USD,
    currentPrice: 120.5,
    quantity: null,
    providerName: 'Lightyear',
    totalInvestment: null,
    currentValue: null,
    profit: null,
    realizedProfit: null,
    unrealizedProfit: null,
    xirr: null,
    platforms: ['LIGHTYEAR'],
    priceChangeAmount: null,
    priceChangePercent: null,
    ter: null,
    xirrAnnualReturn: null,
    firstTransactionDate: null,
  }

  it('keeps the filter chips hidden until the filters toggle is pressed', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(mockHoldings)
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })

    const wrapper = mount(EtfBreakdown)

    await flushPromises()
    expect(wrapper.find('.etf-buttons').exists()).toBe(false)

    await wrapper.find('.dropdown-toggle').trigger('click')

    expect(wrapper.find('.etf-buttons').exists()).toBe(true)
  })

  it('marks the filters toggle active when a platform is deselected while every ETF is selected', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue([
      { ...mockHoldings[0], platforms: 'LIGHTYEAR,SWEDBANK' },
    ])
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })
    localStorage.setItem('portfolio_etf_breakdown_platforms', JSON.stringify(['LIGHTYEAR']))

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

    expect(wrapper.find('.dropdown-toggle').classes()).toContain('active')
  })

  it('shows a currency flag next to ETFs with fundCurrency', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(mockHoldings)
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })

    const wrapper = mount(EtfBreakdown)

    await flushPromises()
    await wrapper.find('.dropdown-toggle').trigger('click')

    const buttons = wrapper.findAll('.etf-btn')
    const vwceBtn = buttons.find(b => b.text().includes('VWCE'))
    expect(vwceBtn).toBeDefined()
    expect(vwceBtn!.find('img').exists()).toBe(true)
    expect(vwceBtn!.find('img').attributes('src')).toContain('/us.svg')
  })

  const buildTwoHoldings = (): EtfHoldingBreakdownDto[] => [
    {
      holdingUuid: 'uuid-apple',
      holdingTicker: 'AAPL',
      holdingName: 'Apple Inc.',
      percentageOfTotal: 66.6667,
      totalValueEur: 10000,
      holdingSector: 'Technology',
      holdingIndustry: 'Technology Hardware, Storage & Peripherals',
      holdingGicsSector: 'Information Technology',
      holdingCountryCode: 'US',
      holdingCountryName: 'United States',
      inEtfs: 'VWCE:XETRA',
      numEtfs: 1,
      platforms: 'LIGHTYEAR',
    },
    {
      holdingUuid: 'uuid-meta',
      holdingTicker: 'META',
      holdingName: 'Meta Platforms Inc.',
      percentageOfTotal: 33.3333,
      totalValueEur: 5000,
      holdingSector: 'Communication Services',
      holdingIndustry: 'Interactive Media & Services',
      holdingGicsSector: 'Communication Services',
      holdingCountryCode: 'US',
      holdingCountryName: 'United States',
      inEtfs: 'VWCE:XETRA',
      numEtfs: 1,
      platforms: 'LIGHTYEAR',
    },
  ]

  const mountWithChartStub = () =>
    mount(EtfBreakdown, {
      global: {
        stubs: {
          EtfBreakdownChart: {
            props: ['chartData', 'benchmarkLabel'],
            template: '<div><slot name="actions" /></div>',
          },
        },
      },
    })

  const clickTab = async (wrapper: VueWrapper, label: string) => {
    const tab = wrapper.findAll('.breakdown-tab').find(btn => btn.text() === label)
    await tab!.trigger('click')
  }

  const withBenchmarkFund = (): EtfHoldingBreakdownDto[] =>
    buildTwoHoldings().map(holding => ({ ...holding, inEtfs: 'WEBN:GER:EUR' }))

  it('does not shrink the summary total value when search narrows the table', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })
    localStorage.setItem('portfolio_etf_search', 'meta')

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

    expect(wrapper.findComponent(EtfBreakdownStats).props('totalValue')).toBe(15000)
  })

  it('keeps the unique holdings count across all holdings when search narrows the table', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })
    localStorage.setItem('portfolio_etf_search', 'meta')

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

    expect(wrapper.findComponent(EtfBreakdownStats).props('uniqueHoldings')).toBe(2)
  })

  it('filters only the holdings table to rows matching the search query', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })
    localStorage.setItem('portfolio_etf_search', 'meta')

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

    expect(wrapper.findComponent(EtfBreakdownTable).props('holdings')).toHaveLength(1)
  })

  it('keeps every sector in the allocation chart when search narrows the table', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })
    localStorage.setItem('portfolio_etf_search', 'meta')

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

    const sectorChart = wrapper.findAllComponents(EtfBreakdownChart)[0]
    expect(sectorChart.props('chartData').map(item => item.label)).toContain('Technology')
  })

  it(`shows the countries breakdown in the chart after the Countries tab is clicked`, async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })

    const wrapper = mount(EtfBreakdown, {
      global: {
        stubs: {
          EtfBreakdownChart: {
            props: ['chartData', 'benchmarkLabel'],
            template: '<div><slot name="actions" /></div>',
          },
        },
      },
    })

    await flushPromises()

    const countriesTab = wrapper.findAll('.breakdown-tab').find(btn => btn.text() === 'Countries')
    await countriesTab!.trigger('click')

    const chart = wrapper.findAllComponents(EtfBreakdownChart)[0]
    expect(chart.props('chartData').map(item => item.label)).toEqual(['United States'])
  })
  it('shows the industries breakdown in the chart after the Industries tab is clicked', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')

    const chart = wrapper.findAllComponents(EtfBreakdownChart)[0]
    expect(chart.props('chartData').map(item => item.label)).toEqual([
      'Technology Hardware, Storage & Peripherals',
      'Interactive Media & Services',
    ])
  })

  it('renders the four breakdown tabs in order', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })

    const wrapper = mountWithChartStub()
    await flushPromises()

    expect(wrapper.findAll('.breakdown-tab').map(btn => btn.text())).toEqual([
      'Sectors',
      'Industries',
      'Top holdings',
      'Countries',
    ])
  })

  it('fetches the benchmark fund breakdown once when the Industries tab opens', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(withBenchmarkFund())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()
    await clickTab(wrapper, 'Sectors')
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    const benchmarkCalls = vi
      .mocked(etfBreakdownService.getBreakdown)
      .mock.calls.filter(
        ([etfs, platforms]) =>
          etfs?.length === 1 && etfs[0] === 'WEBN:GER:EUR' && platforms === undefined
      )
    expect(benchmarkCalls).toHaveLength(1)
  })

  it('attaches the benchmark ratio to each industry once the benchmark is loaded', async () => {
    const holdings = withBenchmarkFund()
    vi.mocked(etfBreakdownService.getBreakdown).mockImplementation(async etfs =>
      etfs?.[0] === 'WEBN:GER:EUR' ? [{ ...holdings[0], percentageOfTotal: 20 }] : holdings
    )
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    const chart = wrapper.findAllComponents(EtfBreakdownChart)[0]
    expect(chart.props('chartData')[0].ratio).toBeCloseTo(3.33, 2)
  })

  it('passes the benchmark fund symbol to the chart once it is loaded', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(withBenchmarkFund())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    expect(wrapper.findAllComponents(EtfBreakdownChart)[0].props('benchmarkLabel')).toBe('WEBN')
  })

  it('passes no benchmark label when no benchmark fund is held', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    expect(wrapper.findAllComponents(EtfBreakdownChart)[0].props('benchmarkLabel')).toBeUndefined()
  })

  it('matches the search query against the industry', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })
    localStorage.setItem('portfolio_etf_search', 'interactive media')

    const wrapper = mount(EtfBreakdown)
    await flushPromises()

    expect(wrapper.findComponent(EtfBreakdownTable).props('holdings')).toHaveLength(1)
  })
})
