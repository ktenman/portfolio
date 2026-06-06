import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import EtfBreakdown from './etf-breakdown.vue'
import EtfBreakdownHeader from './etf-breakdown-header.vue'
import EtfBreakdownTable from './etf-breakdown-table.vue'
import EtfBreakdownChart from './etf-breakdown-chart.vue'
import { etfBreakdownService } from '../../services/etf-breakdown-service'
import { instrumentsService } from '../../services/instruments-service'
import { Currency } from '../../models/generated/domain-models'
import type { EtfHoldingBreakdownDto, InstrumentDto } from '../../models/generated/domain-models'

vi.mock('../../services/etf-breakdown-service', () => ({
  etfBreakdownService: {
    getBreakdown: vi.fn(),
  },
}))

vi.mock('../../services/instruments-service', () => ({
  instrumentsService: {
    getAll: vi.fn(),
    refreshPrices: vi.fn().mockResolvedValue({ status: 'ok' }),
  },
}))

vi.mock('../../services/logo-service', () => ({
  logoService: {
    prefetchCandidates: vi.fn().mockResolvedValue(undefined),
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

  it('shows a currency flag next to ETFs with fundCurrency', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(mockHoldings)
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

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
      holdingCountryCode: 'US',
      holdingCountryName: 'United States',
      inEtfs: 'VWCE:XETRA',
      numEtfs: 1,
      platforms: 'LIGHTYEAR',
    },
  ]

  it('does not shrink the summary total value when search narrows the table', async () => {
    vi.mocked(etfBreakdownService.getBreakdown).mockResolvedValue(buildTwoHoldings())
    vi.mocked(instrumentsService.getAll).mockResolvedValue({
      instruments: [mockInstrument],
      portfolioXirr: null,
    })
    localStorage.setItem('portfolio_etf_search', 'meta')

    const wrapper = mount(EtfBreakdown)

    await flushPromises()

    expect(wrapper.findComponent(EtfBreakdownHeader).props('totalValue')).toBe(15000)
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

    expect(wrapper.findComponent(EtfBreakdownHeader).props('uniqueHoldings')).toBe(2)
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
})
