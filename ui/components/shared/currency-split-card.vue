<template>
  <div
    v-if="entries.length > 0"
    class="currency-split-card tw:min-w-40 tw:rounded-container tw:border tw:border-hairline tw:bg-surface tw:px-4 tw:py-3 tw:shadow-card tw:max-md:px-3.5 tw:max-md:py-2.5"
  >
    <div
      class="currency-split-label tw:mb-2 tw:text-xs tw:font-medium tw:tracking-wider tw:text-gray-600 tw:uppercase"
    >
      {{ label }}
    </div>
    <div class="tw:flex tw:flex-col tw:gap-1">
      <div
        v-for="row in rows"
        :key="row.currency"
        class="currency-split-row tw:flex tw:items-center tw:gap-2 tw:text-sm tw:text-ink"
      >
        <CurrencyFlag :currency="row.currency" :size="14" />
        <span class="tw:min-w-10 tw:font-semibold">{{ row.currency }}</span>
        <span class="tw:ml-auto tw:font-semibold" data-testid="currency-split-pct">
          {{ formatPercent(row.pct) }}
        </span>
        <span
          v-if="showValue"
          class="currency-value tw:min-w-16 tw:text-right tw:text-2xs tw:text-gray-600"
        >
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
