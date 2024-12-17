import { Platform } from './platform'

export interface PortfolioTransaction {
  id?: number
  instrumentId: number
  transactionType: 'BUY' | 'SELL'
  quantity: number
  price: number
  transactionDate: string
  realizedProfit?: number
  unrealizedProfit?: number
  averageCost?: number
  platform: Platform
}
