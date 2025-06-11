export interface Instrument {
  id?: number
  symbol: string
  name: string
  type?: string
  category?: string
  currency?: string
  baseCurrency?: string
  providerName?: 'ALPHA_VANTAGE' | 'BINANCE' | 'FT'
  xirr?: number
  totalInvestment?: number
  currentValue?: number
  profit?: number
  currentPrice?: number
  quantity?: number
  platform?: string
  priceChangePercent24h?: number
}
