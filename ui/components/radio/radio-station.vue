<template>
  <aside class="flex flex-col gap-4" aria-label="Recorder and broadcast">
    <radio-card title="Recorder">
      <template v-if="station" #aside>
        <radio-badge :tone="RECORDER_STATES[station.recorder].tone">
          {{ RECORDER_STATES[station.recorder].label }}
        </radio-badge>
      </template>
      <radio-facts :rows="recorder" />
    </radio-card>
    <radio-on-air aside class="max-lg:hidden" />
  </aside>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRadioStation } from '../../composables/use-radio-recordings'
import { audioFormat, clock, day, RECORDER_STATES } from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'
import RadioOnAir from './radio-on-air.vue'

const { data: station } = useRadioStation()

const recorder = computed<[string, string][]>(() => {
  const s = station.value
  if (!s) return []
  const next = s.capture
    ? `${s.capture.programmes.join(', ')}, ${day(s.capture.slot.start)} ${clock(s.capture.slot.start, 0)}`
    : 'None selected'
  const last = s.last ? `${s.last.programme}, ${day(s.last.end)} ${clock(s.last.end, 0)}` : '—'
  return [
    ['Last', last],
    ['Next', next],
    ['Format', audioFormat(s.format)],
    ['Free space', s.freeBytes == null ? '—' : `${Math.round(s.freeBytes / 1e9)} GB`],
    ['Room for', s.hours == null ? '—' : `≈ ${s.hours.toLocaleString('en-US')} h`],
  ]
})
</script>
