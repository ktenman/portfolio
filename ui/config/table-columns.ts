import type { ColumnDefinition } from '../components/shared/data-table.vue'
import {
  formatCurrency,
  formatDate,
  formatPercentageFromDecimal,
  formatQuantity,
} from '../utils/formatters'

export const instrumentColumns: ColumnDefinition[] = [
  { key: 'instrument', label: 'Instrument' },
  { key: 'type', label: 'Type', class: 'hide-on-mobile' },
  { key: 'quantity', label: 'Quantity', formatter: formatQuantity },
  { key: 'currentPrice', label: 'Price', formatter: formatCurrency },
  { key: 'xirr', label: 'XIRR', formatter: formatPercentageFromDecimal },
  { key: 'totalInvestment', label: 'Invested', formatter: formatCurrency, class: 'hide-on-mobile' },
  { key: 'currentValue', label: 'Value', formatter: formatCurrency },
  { key: 'profit', label: 'Profit', formatter: formatCurrency },
]

export const transactionColumns: ColumnDefinition[] = [
  { key: 'transactionDate', label: 'Date', formatter: formatDate },
  { key: 'instrumentId', label: 'Instrument' },
  { key: 'quantity', label: 'Quantity', formatter: formatQuantity },
  { key: 'price', label: 'Price', formatter: formatCurrency },
  { key: 'amount', label: 'Amount' },
  { key: 'profit', label: 'Profit' },
  { key: 'averageCost', label: 'Average Cost', formatter: formatCurrency, class: 'hide-on-mobile' },
]
