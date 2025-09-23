import { Platform } from './platform'

export interface PortfolioTransaction {
  id?: number
  instrumentId: number
  symbol: string
  transactionType: 'BUY' | 'SELL'
  quantity: number
  remainingQuantity?: number
  price: number
  transactionDate: string
  realizedProfit?: number | null
  unrealizedProfit?: number | null
  averageCost?: number | null
  platform: Platform
}
