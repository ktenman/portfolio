<template>
  <div class="mb-3">
    <div class="flex" role="group" aria-label="Interval">
      <button
        v-for="option in OPTIONS"
        :key="option.interval"
        class="relative border border-hairline bg-surface-sunken px-2.75 py-1.25 text-label font-medium text-ink-soft transition-colors first:rounded-l-container last:-ml-px last:rounded-r-container hover:not-aria-pressed:bg-surface-hover hover:not-aria-pressed:text-ink focus-visible:z-2 aria-pressed:z-1 aria-pressed:border-brass aria-pressed:bg-brass-wash aria-pressed:text-brass-deep max-sm:flex-1 md-down:before:absolute md-down:before:inset-x-0 md-down:before:top-1/2 md-down:before:h-11 md-down:before:-translate-y-1/2"
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
