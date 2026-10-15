<template>
  <div class="platform-buttons" role="group" aria-label="Interval">
    <button
      v-for="option in INTERVALS"
      :key="option.value"
      class="platform-btn md-down:min-h-11"
      :class="{ active: selected === option.value }"
      :aria-pressed="selected === option.value"
      type="button"
      @click="emit('select', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<script lang="ts" setup>
import type { SummaryInterval } from '../../composables/use-summary-interval'

const INTERVALS: { value: SummaryInterval; label: string }[] = [
  { value: 'daily', label: 'Daily' },
  { value: 'monthly', label: 'Monthly' },
]

defineProps<{ selected: SummaryInterval }>()

const emit = defineEmits<{ select: [value: SummaryInterval] }>()
</script>
