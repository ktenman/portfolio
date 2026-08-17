import type { ColumnDefinition } from '../components/shared/data-table.vue'
import {
  formatCurrency,
  formatDate,
  formatPercentageFromDecimal,
  formatQuantity,
} from '../utils/formatters'

export const instrumentColumns: ColumnDefinition[] = [
  { key: 'instrument', label: 'Instrument', sortKey: 'name' },
  { key: 'quantity', label: 'Quantity', formatter: formatQuantity, class: 'text-right!' },
  {
    key: 'currentPrice',
    label: 'Price',
    formatter: formatCurrency,
    class: 'current-price-column text-right!',
  },
  { key: 'currentValue', label: 'Value', formatter: formatCurrency, class: 'text-right!' },
  {
    key: 'totalInvestment',
    label: 'Invested',
    formatter: formatCurrency,
    class: 'hidden! md:table-cell! text-right!',
    hideOnMobile: true,
  },
  { key: 'profit', label: 'Profit', formatter: formatCurrency, class: 'profit-column text-right!' },
  {
    key: 'unrealizedProfit',
    label: 'Unrealized',
    formatter: formatCurrency,
    class: 'unrealized-column text-right!',
  },
  {
    key: 'priceChange',
    label: '1D',
    class: 'hidden! lg:table-cell! price-change-column text-right!',
    hideOnMobile: true,
    sortKey: 'priceChangeAmount',
  },
  { key: 'xirr', label: 'XIRR', formatter: formatPercentageFromDecimal, class: 'text-right!' },
  {
    key: 'xirrAnnualReturn',
    label: 'Annual',
    formatter: formatPercentageFromDecimal,
    class: 'hidden! xl:table-cell! text-right!',
    hideOnMobile: true,
  },
  {
    key: 'portfolioWeight',
    label: 'Weight',
    class: 'hidden! xl:table-cell! weight-column text-right!',
    hideOnMobile: true,
    sortKey: 'currentValue',
  },
  { key: 'ter', label: 'TER', class: 'hidden! xl:table-cell! text-right!', hideOnMobile: true },
]

export const transactionColumns: ColumnDefinition[] = [
  { key: 'transactionDate', label: 'Date', formatter: formatDate },
  { key: 'instrumentId', label: 'Instrument' },
  { key: 'quantityInfo', label: 'Quantity', class: 'text-right!' },
  { key: 'price', label: 'Price', formatter: formatCurrency, class: 'text-right!' },
  { key: 'amount', label: 'Amount', class: 'text-right!' },
  { key: 'profit', label: 'Profit', class: 'text-right!' },
  {
    key: 'averageCost',
    label: 'Average Cost',
    formatter: formatCurrency,
    class: 'hidden! sm:table-cell! text-right!',
    hideOnMobile: true,
  },
]
