import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import EtfBreakdown from './etf-breakdown.vue'
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
})
