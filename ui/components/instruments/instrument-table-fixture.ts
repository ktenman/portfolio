import { mount } from '@vue/test-utils'
import InstrumentTable from './instrument-table.vue'
import type { InstrumentDto } from '../../models/generated/domain-models'
import { ProviderName, TimeRange } from '../../models/generated/domain-models'
import { createInstrumentDto, mockPlatforms } from '../../tests/fixtures'
import { setPlatformDisplayNames } from '../../utils/platform-utils'

setPlatformDisplayNames(mockPlatforms)

const mockInstruments = [
  createInstrumentDto({
    id: 1,
    symbol: 'AAPL',
    name: 'Apple Inc.',
    category: 'STOCK',
    providerName: ProviderName.FT,
    currentPrice: 150.5,
    totalInvestment: 10000,
    currentValue: 15050,
    profit: 5050,
    baseCurrency: 'USD',
    platforms: ['TRADING212'],
  }),
  createInstrumentDto({
    id: 2,
    symbol: 'BTC',
    name: 'Bitcoin',
    category: 'CRYPTO',
    providerName: ProviderName.BINANCE,
    currentPrice: 45000,
    totalInvestment: 20000,
    currentValue: 18000,
    profit: -2000,
    baseCurrency: 'EUR',
    platforms: ['BINANCE', 'COINBASE'],
  }),
  createInstrumentDto({
    id: 3,
    symbol: 'ETH',
    name: 'Ethereum',
    providerName: ProviderName.BINANCE,
    currentPrice: 3000,
    totalInvestment: 5000,
    currentValue: 5000,
    profit: 0,
    baseCurrency: 'USD',
    platforms: ['BINANCE'],
  }),
  createInstrumentDto({
    id: 4,
    symbol: 'UNKNOWN',
    name: 'Unknown Asset',
    providerName: ProviderName.FT,
    currentPrice: 100,
    totalInvestment: 1000,
    currentValue: 900,
    profit: undefined,
    baseCurrency: 'USD',
  }),
]

export const createWrapper = (props = {}) =>
  mount(InstrumentTable, {
    props: {
      instruments: mockInstruments,
      portfolioXirr: 0.125,
      selectedPeriod: TimeRange.ONE_DAY,
      ...props,
    },
  })

export const createWrapperWithInstrument = (overrides: Partial<InstrumentDto>, props = {}) =>
  createWrapper({ instruments: [createInstrumentDto(overrides)], ...props })
