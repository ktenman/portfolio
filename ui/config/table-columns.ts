import type { ColumnDefinition } from '../components/shared/data-table.vue'
import {
  formatCurrency,
  formatDate,
  formatPercentageFromDecimal,
  formatQuantity,
} from '../utils/formatters'

export const instrumentColumns: ColumnDefinition[] = [
  { key: 'instrument', label: 'Instrument' },
  { key: 'type', label: 'Type', class: 'd-none d-md-table-cell' },
  { key: 'quantity', label: 'Quantity', formatter: formatQuantity },
  { key: 'currentPrice', label: 'Price', formatter: formatCurrency },
  { key: 'xirr', label: 'XIRR', formatter: formatPercentageFromDecimal },
  { key: 'currentValue', label: 'Value', formatter: formatCurrency },
  {
    key: 'totalInvestment',
    label: 'Invested',
    formatter: formatCurrency,
    class: 'd-none d-md-table-cell',
  },
  { key: 'profit', label: 'Profit', formatter: formatCurrency },
]

export const transactionColumns: ColumnDefinition[] = [
  { key: 'transactionDate', label: 'Date', formatter: formatDate },
  { key: 'instrumentId', label: 'Instrument' },
  { key: 'platform', label: 'Platform', class: 'd-none d-lg-table-cell' },
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
