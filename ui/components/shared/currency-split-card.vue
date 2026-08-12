<template>
  <div
    v-if="entries.length > 0"
    class="currency-split-card min-w-40 rounded-container border border-hairline bg-surface px-4 py-3 shadow-card max-md:px-3.5 max-md:py-2.5"
  >
    <div
      class="currency-split-label mb-2 text-xs font-medium tracking-wider text-gray-600 uppercase"
    >
      {{ label }}
    </div>
    <div class="flex flex-col gap-1">
      <div
        v-for="row in rows"
        :key="row.currency"
        class="currency-split-row flex items-center gap-2 text-sm text-ink"
      >
        <CurrencyFlag :currency="row.currency" :size="14" />
        <span class="min-w-10 font-semibold">{{ row.currency }}</span>
        <span class="ml-auto font-semibold" data-testid="currency-split-pct">
          {{ formatPercent(row.pct) }}
        </span>
        <span v-if="showValue" class="currency-value min-w-16 text-right text-2xs text-gray-600">
          {{ formatValue(row.value) }}
        </span>
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
