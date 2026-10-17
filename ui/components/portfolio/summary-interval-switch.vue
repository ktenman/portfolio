<template>
  <div class="mb-3">
    <div class="platform-buttons" role="group" aria-label="Interval">
      <button
        v-for="option in OPTIONS"
        :key="option.interval"
        class="platform-btn md-down:min-h-11"
        :class="{ active: option.interval === selected }"
        :aria-pressed="option.interval === selected"
        type="button"
        @click="emit('select', option.interval)"
      >
        {{ option.label }}
      </button>
    </div>
    <p class="sr-only" role="status">{{ announcement }}</p>
  </div>
</template>

<script setup lang="ts">
import type { SummaryInterval } from '../../composables/use-summary-interval'

const OPTIONS: { interval: SummaryInterval; label: string }[] = [
  { interval: 'daily', label: 'Daily' },
  { interval: 'monthly', label: 'Monthly' },
]

defineProps<{
  selected: SummaryInterval
  announcement: string
}>()

const emit = defineEmits<{
  select: [interval: SummaryInterval]
}>()
</script>
