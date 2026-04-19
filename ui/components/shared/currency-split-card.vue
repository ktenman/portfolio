<template>
  <div v-if="entries.length > 0" class="currency-split-card">
    <div class="currency-split-label">{{ label }}</div>
    <div class="currency-split-rows">
      <div v-for="row in rows" :key="row.currency" class="currency-split-row">
        <CurrencyFlag :currency="row.currency" :size="14" />
        <span class="currency-code">{{ row.currency }}</span>
        <span class="currency-percent">{{ formatPercent(row.pct) }}</span>
        <span v-if="showValue" class="currency-value">{{ formatValue(row.value) }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import CurrencyFlag from './currency-flag.vue'

const props = withDefaults(
  defineProps<{
    label?: string
    entries: Array<{ currency: string; value: number }>
    showValue?: boolean
    formatValue?: (value: number) => string
  }>(),
  {
    label: 'Currency Split',
    showValue: false,
    formatValue: (value: number) => value.toFixed(0),
  }
)

const total = computed(() => props.entries.reduce((sum, e) => sum + e.value, 0))

const rows = computed(() =>
  props.entries
    .map(e => ({
      currency: e.currency,
      value: e.value,
      pct: total.value > 0 ? (e.value / total.value) * 100 : 0,
    }))
    .sort((a, b) => b.value - a.value)
)

const formatPercent = (pct: number) => `${pct.toFixed(1)}%`
</script>

<style scoped>
.currency-split-card {
  background: white;
  border: 1px solid #e0e0e0;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  min-width: 160px;
}

.currency-split-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #6c757d;
  font-weight: 500;
  margin-bottom: 0.5rem;
}

.currency-split-rows {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.currency-split-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: #1a1a1a;
}

.currency-code {
  font-weight: 600;
  min-width: 2.5rem;
}

.currency-percent {
  font-weight: 600;
  margin-left: auto;
}

.currency-value {
  color: #6c757d;
  font-size: 0.8125rem;
  min-width: 4rem;
  text-align: right;
}

@media (max-width: 768px) {
  .currency-split-card {
    padding: 0.625rem 0.875rem;
  }
}
</style>
