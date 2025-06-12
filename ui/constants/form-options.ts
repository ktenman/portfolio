import { Platform } from '../models/platform'

export const currencyOptions = [
  { value: 'USD', text: 'USD' },
  { value: 'EUR', text: 'EUR' },
  { value: 'GBP', text: 'GBP' },
]

export const platformOptions = Object.values(Platform).map(value => ({
  value,
  text: value,
}))

export const categoryOptions = [
  { value: 'CRYPTOCURRENCY', text: 'CRYPTOCURRENCY' },
  { value: 'ETF', text: 'ETF' },
  { value: 'STOCK', text: 'STOCK' },
]

export const transactionTypeOptions = [
  { value: 'BUY', text: 'Buy' },
  { value: 'SELL', text: 'Sell' },
]
