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
  const mockHoldings: EtfHoldingBreakdownDto[] = [
    {
      holdingUuid: 'uuid-1',
      holdingTicker: 'AAPL',
      holdingName: 'Apple Inc.',
      percentageOfTotal: 25,
      totalValueEur: 10000,
      holdingSector: 'Technology',
      holdingIndustry: 'Technology Hardware, Storage & Peripherals',
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

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })
  })

  it('keeps the filter chips hidden until the filters toggle is pressed', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(mockHoldings)

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
    localStorage.setItem('portfolio_etf_breakdown_platforms', JSON.stringify(['LIGHTYEAR']))

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

    expect(wrapper.find('.dropdown-toggle').classes()).toContain('active')
  })

  it('shows a currency flag next to ETFs with fundCurrency', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(mockHoldings)

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

  const BENCHMARK = 'WEBN:GER:EUR'

  const withBenchmarkFund = (): EtfHoldingBreakdownDto[] =>
    buildTwoHoldings().map(holding => ({ ...holding, inEtfs: `${BENCHMARK}, VWCE:XETRA` }))

  const benchmarkCalls = () =>
    vi
      .mocked(etfBreakdownService.getBreakdown)
      .mock.calls.filter(([etfs]) => etfs?.[0] === BENCHMARK)

  it('does not shrink the summary total value when search narrows the table', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    localStorage.setItem('portfolio_etf_search', 'meta')

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

    expect(wrapper.findComponent(EtfBreakdownStats).props('totalValue')).toBe(15000)
  })

  it('keeps the unique holdings count across all holdings when search narrows the table', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    localStorage.setItem('portfolio_etf_search', 'meta')

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

    expect(wrapper.findComponent(EtfBreakdownStats).props('uniqueHoldings')).toBe(2)
  })

  it('filters only the holdings table to rows matching the search query', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    localStorage.setItem('portfolio_etf_search', 'meta')

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

    expect(wrapper.findComponent(EtfBreakdownTable).props('holdings')).toHaveLength(1)
  })

  it('keeps every sector in the allocation chart when search narrows the table', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    localStorage.setItem('portfolio_etf_search', 'meta')

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

    const sectorChart = wrapper.findAllComponents(EtfBreakdownChart)[0]
    expect(sectorChart.props('chartData').map(item => item.label)).toContain('Technology')
  })

  it(`shows the countries breakdown in the chart after the Countries tab is clicked`, async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Countries')

    const chart = wrapper.findAllComponents(EtfBreakdownChart)[0]
    expect(chart.props('chartData').map(item => item.label)).toEqual(['United States'])
  })

  it('shows the industries breakdown in the chart after the Industries tab is clicked', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())

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

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()
    await clickTab(wrapper, 'Sectors')
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    expect(benchmarkCalls()).toEqual([[[BENCHMARK], undefined]])
  })

  it('attaches the benchmark ratio to each industry once the benchmark is loaded', async () => {
    const holdings = withBenchmarkFund()
    vi.mocked(etfBreakdownService.getBreakdown).mockImplementation(async etfs =>
      etfs?.[0] === BENCHMARK ? [{ ...holdings[0], percentageOfTotal: 20 }] : holdings
    )

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    const chart = wrapper.findAllComponents(EtfBreakdownChart)[0]
    expect(chart.props('chartData')[0].ratio).toBeCloseTo(3.33, 2)
  })

  it('hides the benchmark comparison when only the benchmark fund is selected', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(withBenchmarkFund())
    localStorage.setItem('portfolio_selected_etfs', JSON.stringify([BENCHMARK]))

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    const chart = wrapper.findAllComponents(EtfBreakdownChart)[0]
    expect(chart.props('chartData').map(item => item.benchmark)).toEqual([undefined, undefined])
  })

  it('passes the benchmark fund symbol to the chart once it is loaded', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(withBenchmarkFund())

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    expect(wrapper.findAllComponents(EtfBreakdownChart)[0].props('benchmarkLabel')).toBe('WEBN')
  })

  it('passes no benchmark label when no benchmark fund is held', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    expect(wrapper.findAllComponents(EtfBreakdownChart)[0].props('benchmarkLabel')).toBeUndefined()
  })

  it('does not fetch the benchmark again while the first request is still in flight', async () => {
    const holdings = withBenchmarkFund()
    vi.mocked(etfBreakdownService.getBreakdown).mockImplementation(etfs =>
      etfs?.[0] === BENCHMARK ? new Promise(() => {}) : Promise.resolve(holdings)
    )

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await clickTab(wrapper, 'Sectors')
    await clickTab(wrapper, 'Industries')

    expect(benchmarkCalls()).toHaveLength(1)
  })

  it('falls back to VWCE when WEBN is not held', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(
      buildTwoHoldings().map(holding => ({ ...holding, inEtfs: 'VWCE:GER:EUR, VWCE:XETRA' }))
    )

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    expect(wrapper.findAllComponents(EtfBreakdownChart)[0].props('benchmarkLabel')).toBe('VWCE')
  })

  it('prefers WEBN when both benchmark funds are held', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(
      buildTwoHoldings().map(holding => ({ ...holding, inEtfs: `${BENCHMARK}, VWCE:GER:EUR` }))
    )

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    expect(wrapper.findAllComponents(EtfBreakdownChart)[0].props('benchmarkLabel')).toBe('WEBN')
  })

  it('hides the comparison and does not retry after the benchmark request fails', async () => {
    const holdings = withBenchmarkFund()
    vi.mocked(etfBreakdownService.getBreakdown).mockImplementation(etfs =>
      etfs?.[0] === BENCHMARK ? Promise.reject(new Error('unavailable')) : Promise.resolve(holdings)
    )

    const wrapper = mountWithChartStub()
    await flushPromises()
    await clickTab(wrapper, 'Industries')
    await flushPromises()
    await clickTab(wrapper, 'Sectors')
    await clickTab(wrapper, 'Industries')
    await flushPromises()

    const chart = wrapper.findAllComponents(EtfBreakdownChart)[0]
    expect([chart.props('chartData')[0].benchmark, benchmarkCalls().length]).toEqual([undefined, 1])
  })

  it('matches the search query against the industry', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    localStorage.setItem('portfolio_etf_search', 'interactive media')

    const wrapper = mount(EtfBreakdown)
    await flushPromises()

    expect(wrapper.findComponent(EtfBreakdownTable).props('holdings')).toHaveLength(1)
  })
})
