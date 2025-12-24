import {
  InstrumentDto,
  TransactionResponseDto,
  PortfolioSummaryDto,
  ProviderName,
  Platform,
  TransactionType,
} from '../models/generated/domain-models'

export const createInstrumentDto = (overrides?: Partial<InstrumentDto>): InstrumentDto => ({
  id: 1,
  symbol: 'TEST',
  name: 'Test Instrument',
  category: 'ETF',
  baseCurrency: 'EUR',
  currentPrice: 100,
  quantity: 10,
  providerName: ProviderName.FT,
  totalInvestment: 1000,
  currentValue: 1000,
  profit: 0,
  realizedProfit: 0,
  unrealizedProfit: 0,
  xirr: 0,
  platforms: [],
  priceChangeAmount: 0,
  priceChangePercent: 0,
  ter: null,
  xirrAnnualReturn: null,
  ...overrides,
})

export const createTransactionDto = (
  overrides?: Partial<TransactionResponseDto>
): TransactionResponseDto => ({
  id: 1,
  instrumentId: 1,
  symbol: 'TEST',
  name: 'Test Instrument',
  transactionType: TransactionType.BUY,
  quantity: 10,
  price: 100,
  transactionDate: '2023-01-01',
  platform: Platform.TRADING212,
  realizedProfit: null,
  unrealizedProfit: 0,
  averageCost: 100,
  remainingQuantity: 10,
  commission: 0,
  currency: 'EUR',
  ...overrides,
})

export const createPortfolioSummaryDto = (
  overrides?: Partial<PortfolioSummaryDto>
): PortfolioSummaryDto => ({
  date: '2023-01-01',
  totalValue: 10000,
  xirrAnnualReturn: 0.1,
  realizedProfit: 0,
  unrealizedProfit: 1000,
  totalProfit: 1000,
  earningsPerDay: 10,
  earningsPerMonth: 300,
  totalProfitChange24h: null,
  ...overrides,
})
