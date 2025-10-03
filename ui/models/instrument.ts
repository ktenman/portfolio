import { ProviderName } from './provider-name'

export interface Instrument {
  id?: number
  symbol: string
  name: string
  type?: string
  category?: string
  currency?: string
  baseCurrency?: string
  providerName?: ProviderName
  xirr?: number
  totalInvestment?: number
  currentValue?: number
  profit?: number
  currentPrice?: number
  quantity?: number
  platform?: string
  priceChangePercent24h?: number
  platforms?: string[]
  priceChangeAmount?: number
  priceChangePercent?: number
}
