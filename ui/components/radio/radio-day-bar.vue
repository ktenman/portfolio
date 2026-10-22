<template>
  <radio-card title="Recorded time">
    <template v-if="radioDay.seconds" #aside>
      <span class="text-sm text-ink-soft">{{ length(radioDay.seconds) }} of 24 h</span>
    </template>
    <div
      class="relative h-6 rounded-control border border-hairline bg-surface-sunken"
      role="img"
      :aria-label="label"
    >
      <span
        v-for="guide in GUIDES"
        :key="guide"
        class="absolute inset-y-0 w-px bg-hairline"
        data-testid="radio-day-guide"
        :style="{ left: guide }"
      />
      <span
        v-for="span in radioDay.recorded"
        :key="span.start"
        class="absolute inset-y-0"
        :class="span.recording ? `rounded-l-[2px] ${STRIPES}` : 'rounded-[2px] bg-umber'"
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
    <p
      class="mt-3 mb-0 flex flex-wrap gap-x-5 gap-y-1 text-sm text-body-secondary"
      data-testid="radio-day-legend"
    >
      <span v-if="finished" class="inline-flex items-center gap-2">
        <span class="size-3 rounded-[2px] bg-umber" />
        {{ finished }}
      </span>
      <span v-if="running" class="inline-flex items-center gap-2">
        <span class="size-3 rounded-[2px]" :class="STRIPES" />
        {{ running }}
      </span>
      <span v-if="current" class="inline-flex items-center gap-2">
        <span class="h-3.5 w-0.5 bg-notice" />
        Now {{ clock(radioDay.now, 0) }}
      </span>
    </p>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { RadioDay, RecordedSpan } from '../../models/generated/domain-models'
import { clock, length, range } from '../../utils/radio'
import RadioCard from './radio-card.vue'

const HOURS = ['00', '06', '12', '18', '24']
const GUIDES = ['25%', '50%', '75%']
const STRIPES =
  'bg-[repeating-linear-gradient(135deg,var(--color-umber)_0_3px,var(--color-brass)_3px_6px)]'

const props = defineProps<{ radioDay: RadioDay }>()

const part = (seconds: number) =>
  `${(seconds / (props.radioDay.until - props.radioDay.from)) * 100}%`

const current = computed(
  () => props.radioDay.now >= props.radioDay.from && props.radioDay.now < props.radioDay.until
)

const spans = (recording: boolean): RecordedSpan[] =>
  props.radioDay.recorded.filter(span => span.recording === recording)

const finished = computed(() => {
  const done = spans(false)
  if (done.length) return `Recorded ${done.map(s => range(s.start, 0, s.end - s.start)).join(', ')}`
  return props.radioDay.recorded.length ? null : 'Nothing recorded'
})

const running = computed(() => {
  const live = spans(true)
  return live.length ? `Recording ${live.map(s => `${clock(s.start, 0)}–`).join(', ')}` : null
})

const label = computed(() => [finished.value, running.value].filter(Boolean).join(', '))
</script>
