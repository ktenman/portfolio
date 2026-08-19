import type { ColumnDefinition } from '../components/shared/data-table.vue'
import { formatCurrency, formatDate, formatQuantity } from '../utils/formatters'

export const instrumentColumns: ColumnDefinition[] = [
  { key: 'instrument', label: 'Instrument', sortKey: 'name' },
  { key: 'quantity', label: 'Quantity', formatter: formatQuantity, class: 'text-right!' },
  { key: 'currentPrice', label: 'Price', class: 'current-price-column text-right!' },
  { key: 'currentValue', label: 'Value', class: 'text-right!' },
  { key: 'totalInvestment', label: 'Invested', class: 'hidden! md:table-cell! text-right!' },
  { key: 'profit', label: 'Profit', class: 'profit-column text-right!' },
  { key: 'unrealizedProfit', label: 'Unrealized', class: 'unrealized-column text-right!' },
  {
    key: 'priceChange',
    label: '1D',
    class: 'hidden! lg:table-cell! price-change-column text-right!',
    sortKey: 'priceChangeAmount',
  },
  { key: 'xirr', label: 'XIRR', class: 'text-right!' },
  { key: 'xirrAnnualReturn', label: 'Annual', class: 'hidden! xl:table-cell! text-right!' },
  {
    key: 'portfolioWeight',
    label: 'Weight',
    class: 'hidden! xl:table-cell! weight-column text-right!',
    sortKey: 'currentValue',
  },
  { key: 'ter', label: 'TER', class: 'hidden! xl:table-cell! text-right!' },
]

export const transactionColumns: ColumnDefinition[] = [
  { key: 'transactionDate', label: 'Date', formatter: formatDate },
  { key: 'instrumentId', label: 'Instrument' },
  { key: 'quantityInfo', label: 'Quantity', class: 'text-right!' },
  { key: 'price', label: 'Price', class: 'text-right!' },
  { key: 'amount', label: 'Amount', class: 'text-right!' },
  { key: 'profit', label: 'Profit', class: 'text-right!' },
  {
    key: 'averageCost',
    label: 'Average Cost',
    formatter: formatCurrency,
    class: 'hidden! sm:table-cell! text-right!',
  },
]
