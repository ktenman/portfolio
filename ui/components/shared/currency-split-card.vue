<template>
  <div
    v-if="entries.length > 0"
    class="currency-split-card card-shell min-w-40 max-md:px-3.5 max-md:py-2.5"
  >
    <div
      class="currency-split-label mb-2.5 text-xs font-medium tracking-wider text-gray-600 uppercase"
    >
      {{ label }}
    </div>
    <div class="currency-split-rows" :class="{ 'with-value': showValue }">
      <div v-for="row in rows" :key="row.currency" class="currency-split-row">
        <CurrencyFlag :currency="row.currency" :size="14" />
        <span class="currency-code">{{ row.currency }}</span>
        <span class="currency-bar"><span :style="{ width: `${row.pct}%` }"></span></span>
        <span class="currency-pct" data-testid="currency-split-pct">
          {{ formatPercent(row.pct) }}
        </span>
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
.currency-split-rows {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto;
  align-items: center;
  column-gap: 0.625rem;
  row-gap: 0.5rem;
}

.currency-split-rows.with-value {
  grid-template-columns: auto auto minmax(0, 1fr) auto auto;
}

.currency-split-row {
  display: contents;
}

.currency-code {
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--color-ink);
}

.currency-bar {
  height: 4px;
  border-radius: var(--radius-control);
  background: var(--color-surface-sunken);
  overflow: hidden;
}

.currency-bar > span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-brass);
}

.currency-pct {
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--color-ink);
  text-align: right;
}

.currency-value {
  font-size: var(--text-2xs);
  color: var(--color-ink-soft);
  text-align: right;
}
</style>
