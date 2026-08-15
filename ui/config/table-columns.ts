import type { ColumnDefinition } from '../components/shared/data-table.vue'
import {
  formatCurrency,
  formatDate,
  formatPercentageFromDecimal,
  formatQuantity,
} from '../utils/formatters'

export const instrumentColumns: ColumnDefinition[] = [
  { key: 'instrument', label: 'Instrument', sortKey: 'name' },
  { key: 'quantity', label: 'Quantity', formatter: formatQuantity },
  { key: 'currentPrice', label: 'Price', formatter: formatCurrency, class: 'current-price-column' },
  { key: 'currentValue', label: 'Value', formatter: formatCurrency },
  {
    key: 'totalInvestment',
    label: 'Invested',
    formatter: formatCurrency,
    class: 'hidden! md:table-cell!',
    hideOnMobile: true,
  },
  { key: 'profit', label: 'Profit', formatter: formatCurrency, class: 'profit-column' },
  {
    key: 'unrealizedProfit',
    label: 'Unrealized',
    formatter: formatCurrency,
    class: 'unrealized-column',
  },
  {
    key: 'priceChange',
    label: '1D',
    class: 'hidden! lg:table-cell! price-change-column',
    hideOnMobile: true,
    sortKey: 'priceChangeAmount',
  },
  { key: 'xirr', label: 'XIRR', formatter: formatPercentageFromDecimal },
  {
    key: 'xirrAnnualReturn',
    label: 'Annual',
    formatter: formatPercentageFromDecimal,
    class: 'hidden! xl:table-cell!',
    hideOnMobile: true,
  },
  {
    key: 'portfolioWeight',
    label: 'Weight',
    class: 'hidden! xl:table-cell! weight-column',
    hideOnMobile: true,
    sortKey: 'currentValue',
  },
  { key: 'ter', label: 'TER', class: 'hidden! xl:table-cell!', hideOnMobile: true },
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
    class: 'hidden! sm:table-cell!',
    hideOnMobile: true,
  },
]
