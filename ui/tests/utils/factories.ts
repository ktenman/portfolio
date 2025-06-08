import type { Instrument } from '../../models/instrument'
import type { PortfolioTransaction } from '../../models/portfolio-transaction'
import type { PortfolioSummary } from '../../models/portfolio-summary'
import type { CalculationResult } from '../../models/calculation-result'
import { Platform } from '../../models/platform'

export const createInstrument = (overrides?: Partial<Instrument>): Instrument => ({
  id: 1,
  symbol: 'AAPL',
  name: 'Apple Inc.',
  category: 'STOCK',
  baseCurrency: 'USD',
  providerName: 'ALPHA_VANTAGE',
  xirr: 15.5,
  totalInvestment: 8000,
  currentValue: 10000,
  profit: 2000,
  currentPrice: 150.0,
  quantity: 100,
  ...overrides,
})

export const createTransaction = (
  overrides?: Partial<PortfolioTransaction>
): PortfolioTransaction => ({
  id: 1,
  instrumentId: 1,
  transactionDate: new Date().toISOString(),
  transactionType: 'BUY' as const,
  quantity: 10,
  price: 150.0,
  platform: Platform.LHV,
  ...overrides,
})

export const createPortfolioSummary = (
  overrides?: Partial<PortfolioSummary>
): PortfolioSummary => ({
  date: new Date().toISOString(),
  totalValue: 10000,
  xirrAnnualReturn: 18.5,
  totalProfit: 2000,
  earningsPerDay: 50,
  earningsPerMonth: 1500,
  ...overrides,
})

export const createCalculationResult = (
  overrides?: Partial<CalculationResult>
): CalculationResult => ({
  xirrs: [
    { date: '2024-01-01', amount: -8000 },
    { date: '2024-12-31', amount: 10000 },
  ],
  median: 15.5,
  average: 18.2,
  total: 2000,
  ...overrides,
})
