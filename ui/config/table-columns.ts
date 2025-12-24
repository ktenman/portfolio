import type { ColumnDefinition } from '../components/shared/data-table.vue'
import {
  formatCurrency,
  formatDate,
  formatPercentageFromDecimal,
  formatQuantity,
} from '../utils/formatters'

export const instrumentColumns: ColumnDefinition[] = [
  { key: 'instrument', label: 'Instrument' },
  { key: 'quantity', label: 'Quantity', formatter: formatQuantity },
  { key: 'currentPrice', label: 'Price', formatter: formatCurrency },
  { key: 'currentValue', label: 'Value', formatter: formatCurrency },
  {
    key: 'totalInvestment',
    label: 'Invested',
    formatter: formatCurrency,
    class: 'd-none d-md-table-cell',
  },
  { key: 'profit', label: 'Profit', formatter: formatCurrency, class: 'profit-column' },
  {
    key: 'unrealizedProfit',
    label: 'Unrealized',
    formatter: formatCurrency,
    class: 'unrealized-column',
  },
  { key: 'priceChange', label: '24H', class: 'd-none d-lg-table-cell price-change-column' },
  { key: 'xirr', label: 'XIRR', formatter: formatPercentageFromDecimal },
  { key: 'portfolioWeight', label: 'Weight', class: 'd-none d-xl-table-cell' },
  { key: 'ter', label: 'TER', class: 'd-none d-xl-table-cell' },
]

export const transactionColumns: ColumnDefinition[] = [
  { key: 'transactionDate', label: 'Date', formatter: formatDate },
  { key: 'instrumentId', label: 'Instrument' },
  { key: 'quantityInfo', label: 'Quantity' },
  { key: 'price', label: 'Price', formatter: formatCurrency },
  { key: 'amount', label: 'Amount' },
  { key: 'profit', label: 'Profit' },
  {
    key: 'averageCost',
    label: 'Average Cost',
    formatter: formatCurrency,
    class: 'd-none d-sm-table-cell',
  },
]
