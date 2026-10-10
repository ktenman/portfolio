import {
  InstrumentDto,
  TransactionResponseDto,
  PortfolioSummaryDto,
  ProviderName,
  Platform,
  TransactionType,
} from '../models/generated/domain-models'
import {
  buildDailyProfitCalendar,
  type DailyProfitCalendar,
} from '../services/daily-profit-calendar'

export const mockPlatforms = [
  { name: 'AVIVA', displayName: 'Aviva' },
  { name: 'BINANCE', displayName: 'Binance' },
  { name: 'COINBASE', displayName: 'Coinbase' },
  { name: 'IBKR', displayName: 'IBKR' },
  { name: 'LHV', displayName: 'LHV' },
  { name: 'LIGHTYEAR', displayName: 'Lightyear' },
  { name: 'LIGHTYEAR_BUSINESS', displayName: 'Lightyear Business' },
  { name: 'SWEDBANK', displayName: 'Swedbank' },
  { name: 'TRADING212', displayName: 'Trading 212' },
  { name: 'UNKNOWN', displayName: 'Unknown' },
]

export const instrumentDataTableStub = {
  name: 'DataTable',
  props: ['items', 'columns', 'isLoading', 'isError', 'errorMessage', 'emptyMessage'],
  template: `
      <div>
        <div class="mobile-cards-wrapper">
          <div v-for="item in items" :key="item.id" class="mobile-card">
            <slot name="mobile-card" :item="item" />
          </div>
          <slot name="mobile-footer" />
        </div>
        <table>
          <tbody>
            <tr v-for="item in items" :key="item.id">
              <td><slot name="cell-instrument" :item="item" /></td>
              <td><slot name="cell-type" :item="item" /></td>
              <td><slot name="cell-currentPrice" :item="item" /></td>
              <td><slot name="cell-totalInvestment" :item="item" /></td>
              <td><slot name="cell-currentValue" :item="item" /></td>
              <td><slot name="cell-profit" :item="item" /></td>
              <td><slot name="actions" :item="item" /></td>
            </tr>
          </tbody>
        </table>
      </div>
    `,
}

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
  firstTransactionDate: '2024-01-01',
  fundCurrency: null,
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

export const createCalendarRow = (
  date: string,
  totalProfit: number,
  totalValue = 100000
): PortfolioSummaryDto =>
  createPortfolioSummaryDto({ date, totalProfit, totalValue, xirrAnnualReturn: 0.2069 })

export const createCalendarRows = (): PortfolioSummaryDto[] => [
  createCalendarRow('2025-12-01', 1000),
  createCalendarRow('2025-12-02', 1300),
  createCalendarRow('2025-12-03', 700),
  createCalendarRow('2025-12-04', 720),
  createCalendarRow('2025-12-06', 1920),
  createCalendarRow('2025-12-07', 1980),
  createCalendarRow('2025-12-08', 1880),
  createCalendarRow('2025-12-09', 2580),
]

export const createDailyProfitCalendar = (
  rows: PortfolioSummaryDto[] = createCalendarRows(),
  today = '2030-01-01'
): DailyProfitCalendar => {
  const calendar = buildDailyProfitCalendar(rows, today)
  if (!calendar) throw new Error('expected a calendar')
  return calendar
}

export class FakeEventSource extends EventTarget {
  static readonly CLOSED = 2
  static instances: FakeEventSource[] = []
  readyState = 0
  onerror: ((event: Event) => void) | null = null

  constructor(readonly url: string) {
    super()
    FakeEventSource.instances.push(this)
  }

  close() {
    this.readyState = FakeEventSource.CLOSED
  }
}
