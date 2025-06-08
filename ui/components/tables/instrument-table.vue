<template>
  <div class="table-responsive">
    <table class="table table-striped table-hover">
      <thead>
        <tr>
          <th>Symbol</th>
          <th>Name</th>
          <th>Currency</th>
          <th class="d-none d-md-table-cell">Quantity</th>
          <th class="d-none d-md-table-cell">Current Price</th>
          <th>XIRR Annual Return</th>
          <th>Invested</th>
          <th>Current Value</th>
          <th>Profit/Loss</th>
          <th class="text-end">Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="instrument in instruments" :key="instrument.id">
          <td data-label="Symbol">{{ instrument.symbol }}</td>
          <td data-label="Name">{{ instrument.name }}</td>
          <td data-label="Currency">{{ instrument.baseCurrency }}</td>
          <td data-label="Quantity" class="d-none d-md-table-cell">
            {{ formatNumber(instrument.quantity) }}
          </td>
          <td data-label="Current Price" class="d-none d-md-table-cell">
            {{ formatCurrency(instrument.currentPrice) }}
          </td>
          <td data-label="XIRR Annual Return">{{ formatPercentage(instrument.xirr) }}</td>
          <td data-label="Invested">{{ formatCurrency(instrument.totalInvestment) }}</td>
          <td data-label="Current Value">{{ formatCurrency(instrument.currentValue) }}</td>
          <td data-label="Profit/Loss" :class="profitClass(instrument)">
            {{ formattedProfit(instrument) }}
          </td>
          <td data-label="Actions" class="text-end">
            <button class="btn btn-sm btn-secondary" @click="$emit('edit', instrument)">
              <font-awesome-icon icon="pencil-alt" />
              <span class="d-none d-md-inline ms-1">Edit</span>
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script lang="ts" setup>
import { Instrument } from '../../models/instrument'
import { useFormatters } from '../../composables/use-formatters'

interface Props {
  instruments: Instrument[]
}

interface Emits {
  (e: 'edit', instrument: Instrument): void
}

defineProps<Props>()
defineEmits<Emits>()

const { formatNumber, formatCurrency, formatPercentage } = useFormatters()

const formattedProfit = (instrument: Instrument): string => {
  if (instrument.profit === 0) {
    return '0.00'
  }
  const formattedAmount = formatCurrency(instrument.profit)
  return instrument.profit > 0
    ? `+${formattedAmount.substring(1)}`
    : `-${formattedAmount.substring(1)}`
}

const profitClass = (instrument: Instrument): string => {
  return instrument.profit >= 0 ? 'text-success' : 'text-danger'
}
</script>

<style scoped>
.table {
  font-size: 0.9rem;
}

.table th,
.table td {
  vertical-align: middle;
}

@media (max-width: 767px) {
  .table {
    display: block;
    width: 100%;
    overflow: hidden;
  }

  .table thead {
    display: none;
  }

  .table tbody,
  .table tr,
  .table td {
    display: block;
    width: 100%;
  }

  .table tr {
    margin-bottom: 1rem;
    border-bottom: 1px solid #dee2e6;
  }

  .table td {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.5rem;
    font-size: 1rem;
    text-align: left;
  }

  .table td[data-label]:before {
    content: attr(data-label);
    font-weight: bold;
    color: #6c757d;
    margin-right: 0.5rem;
    width: 50%;
    flex-shrink: 0;
  }

  .table td.text-end {
    justify-content: flex-end;
  }
}
</style>

