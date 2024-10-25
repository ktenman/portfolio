export interface Instrument {
  id?: number
  symbol: string
  name: string
  category: string
  baseCurrency: string
  providerName: 'ALPHA_VANTAGE' | 'BINANCE'
  xirr: number,
  totalInvestment: number,
  currentValue: number,
  profit: number,
}
