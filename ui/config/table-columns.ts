import type { ColumnDefinition } from '../components/shared/data-table.vue'
import {
  formatCurrency,
  formatDate,
  formatNumber,
  formatPercentageFromDecimal,
} from '../utils/formatters'

export const instrumentColumns: ColumnDefinition[] = [
  { key: 'symbol', label: 'Symbol' },
  { key: 'name', label: 'Name' },
  { key: 'baseCurrency', label: 'Currency' },
  { key: 'quantity', label: 'Quantity', formatter: formatNumber },
  { key: 'currentPrice', label: 'Current Price', formatter: formatCurrency },
  { key: 'xirr', label: 'XIRR Annual Return', formatter: formatPercentageFromDecimal },
  { key: 'totalInvestment', label: 'Invested', formatter: formatCurrency },
  { key: 'currentValue', label: 'Current Value', formatter: formatCurrency },
  { key: 'profit', label: 'Profit/Loss', formatter: formatCurrency },
]

export const transactionColumns: ColumnDefinition[] = [
  { key: 'transactionDate', label: 'Date', formatter: formatDate },
  { key: 'instrumentId', label: 'Instrument' },
  { key: 'quantity', label: 'Quantity', formatter: formatNumber },
  { key: 'price', label: 'Price', formatter: formatCurrency },
  { key: 'amount', label: 'Amount' },
  { key: 'profit', label: 'Profit/Loss' },
  { key: 'averageCost', label: 'Average Cost', formatter: formatCurrency },
]
