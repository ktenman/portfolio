<template>
  <div class="table-responsive">
    <table class="table table-striped">
      <thead>
        <tr>
          <th>Date</th>
          <th>XIRR Annual Return</th>
          <th class="hide-on-mobile">Earnings Per Day</th>
          <th>Earnings Per Month</th>
          <th>Total Profit</th>
          <th>Total Value</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(summary, index) in summaries"
          :key="summary.date"
          :class="getRowClass(summary, index)"
        >
          <td>{{ formatDate(summary.date) }}</td>
          <td>{{ formatPercentageFromDecimal(summary.xirrAnnualReturn) }}</td>
          <td class="hide-on-mobile">
            {{ formatCurrencyWithSymbol(summary.earningsPerDay) }}
          </td>
          <td>{{ formatCurrencyWithSymbol(summary.earningsPerMonth) }}</td>
          <td>{{ formatCurrencyWithSymbol(summary.totalProfit) }}</td>
          <td>{{ formatCurrencyWithSymbol(summary.totalValue) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { PortfolioSummary } from '../../models/portfolio-summary'
import {
  formatCurrencyWithSymbol,
  formatDate,
  formatPercentageFromDecimal,
} from '../../utils/formatters'

interface Props {
  summaries: PortfolioSummary[]
}

defineProps<Props>()

const isToday = (dateString: string) => {
  return dateString === new Date().toISOString().split('T')[0]
}

const getRowClass = (summary: PortfolioSummary, index: number) => ({
  'font-weight-bold': index === 0 && isToday(summary.date),
})
</script>

<style scoped>
@media (max-width: 767px) {
  .table {
    font-size: 12px;
  }

  .hide-on-mobile {
    display: none;
  }
}
</style>
