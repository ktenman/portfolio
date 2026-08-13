<template>
  <div v-if="totalValue > 0" class="breakdown-stats">
    <StatCard label="Total Value" :value="formatCurrencyWithSymbol(totalValue)" />
    <StatCard label="Unique Holdings" :value="String(uniqueHoldings)" />
    <CurrencySplitCard
      v-if="currencySplit && currencySplit.length > 0"
      label="Fund Currency"
      :entries="currencySplit"
      :show-value="true"
      :format-value="formatCurrencyWithSymbol"
    />
  </div>
</template>

<script lang="ts" setup>
import CurrencySplitCard from '../shared/currency-split-card.vue'
import StatCard from '../shared/stat-card.vue'
import { formatCurrencyWithSymbol } from '../../utils/formatters'

defineProps<{
  totalValue: number
  uniqueHoldings: number
  currencySplit?: Array<{ currency: string; value: number }>
}>()
</script>

<style scoped>
.breakdown-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  align-content: start;
  gap: 0.75rem;
}

.breakdown-stats > .currency-split-card {
  grid-column: 1 / -1;
}
</style>
