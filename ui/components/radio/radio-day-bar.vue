<template>
  <radio-card title="Salvestatud aeg">
    <template v-if="radioDay.seconds" #aside>
      <span class="text-sm text-ink-soft">{{ length(radioDay.seconds) }} ööpäevast</span>
    </template>
    <div
      class="relative h-6 rounded-control border border-hairline bg-surface-sunken"
      role="img"
      :aria-label="recorded"
    >
      <span
        v-for="span in radioDay.recorded"
        :key="span.start"
        class="absolute inset-y-0 rounded-[2px] bg-ink"
        data-testid="radio-day-span"
        :style="{ left: part(span.start - radioDay.from), width: part(span.end - span.start) }"
      />
      <span
        v-if="current"
        class="absolute -inset-y-1 w-0.5 bg-notice"
        data-testid="radio-day-now"
        :style="{ left: part(radioDay.now - radioDay.from) }"
      />
    </div>
    <div class="mt-2 flex justify-between text-xs text-ink-soft tabular-nums" aria-hidden="true">
      <span v-for="hour in HOURS" :key="hour">{{ hour }}</span>
    </div>
    <p class="mt-3 mb-0 flex flex-wrap gap-x-5 gap-y-1 text-sm text-body-secondary">
      <span class="inline-flex items-center gap-2">
        <span class="size-3 rounded-[2px] bg-ink" />
        {{ recorded }}
      </span>
      <span v-if="current" class="inline-flex items-center gap-2">
        <span class="h-3.5 w-0.5 bg-notice" />
        Praegu {{ clock(radioDay.now, 0) }}
      </span>
    </p>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { RadioDay } from '../../models/generated/domain-models'
import { clock, length, range } from '../../utils/radio'
import RadioCard from './radio-card.vue'

const HOURS = ['00', '06', '12', '18', '24']

const props = defineProps<{ radioDay: RadioDay }>()

const part = (seconds: number) =>
  `${(seconds / (props.radioDay.until - props.radioDay.from)) * 100}%`

const current = computed(
  () => props.radioDay.now >= props.radioDay.from && props.radioDay.now < props.radioDay.until
)

const recorded = computed(() => {
  const spans = props.radioDay.recorded
  if (!spans.length) return 'Midagi pole salvestatud'
  return `Salvestatud ${spans.map(s => range(s.start, 0, s.end - s.start)).join(', ')}`
})
</script>
