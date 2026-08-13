<template>
  <div class="summary-cards mb-6">
    <StatCard label="Weighted TER" :value="formatTer(weightedTer, 3)" />
    <StatCard label="Weighted Return" :value="formatReturn(weightedAnnualReturn)" />
    <StatCard label="Unique Holdings" :value="totalUniqueHoldings.toLocaleString()" />
    <StatCard label="Top 10 Concentration" :value="formatPercentage(top10Percentage)" />
    <CurrencySplitCard
      v-if="currencySplit && currencySplit.length > 0"
      label="Fund Currency"
      :entries="currencySplit"
    />
  </div>
</template>

<script lang="ts" setup>
import CurrencySplitCard from '../shared/currency-split-card.vue'
import StatCard from '../shared/stat-card.vue'
import { formatTer, formatReturn, formatPercentage } from '../../utils/formatters'

defineProps<{
  weightedTer: number
  weightedAnnualReturn: number
  totalUniqueHoldings: number
  top10Percentage: number
  currencySplit?: Array<{ currency: string; value: number }>
}>()
</script>

<style scoped>
.summary-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(9.5rem, 1fr));
  align-content: start;
  gap: 0.75rem;
}
</style>
