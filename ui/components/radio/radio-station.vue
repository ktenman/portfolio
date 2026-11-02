<template>
  <aside class="flex flex-col gap-4" :aria-label="`${title} and broadcast`">
    <radio-on-air aside class="max-lg:hidden" />
    <radio-card :title="title">
      <template v-if="chosen && stations[0]" #aside>
        <radio-badge :tone="RECORDER_STATES[stations[0].recorder].tone">
          {{ RECORDER_STATES[stations[0].recorder].label }}
        </radio-badge>
      </template>
      <ul v-if="!chosen" class="mb-0">
        <li
          v-for="s in stations"
          :key="s.station"
          class="border-b border-hairline py-2.5"
          data-testid="radio-recorder"
        >
          <span class="flex items-baseline justify-between gap-2">
            <span class="font-semibold">{{ STATIONS[s.station] }}</span>
            <radio-badge :tone="RECORDER_STATES[s.recorder].tone">
              {{ RECORDER_STATES[s.recorder].label }}
            </radio-badge>
          </span>
          <span class="mt-1 block text-sm text-ink-soft">
            {{ s.capture ? `Next ${upcoming(s)}` : 'No show selected' }}
          </span>
          <span v-if="s.format" class="block text-sm text-ink-soft">
            {{ audioFormat(s.format) }}
          </span>
        </li>
      </ul>
      <radio-facts :rows="facts" />
    </radio-card>
  </aside>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { Station, type RadioStation } from '../../models/generated/domain-models'
import { useRailStations, useStationFilter } from '../../composables/use-radio-recordings'
import { audioFormat, clock, day, RECORDER_STATES, STATIONS } from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'
import RadioOnAir from './radio-on-air.vue'

const stations = useRailStations()
const chosen = useStationFilter()

const title = computed(() => (chosen.value ? 'Recorder' : 'Recorders'))

const upcoming = ({ capture }: RadioStation) =>
  capture
    ? `${capture.programmes.join(', ')}, ${day(capture.slot.start)} ${clock(capture.slot.start, 0)}`
    : 'None selected'

const facts = computed<[string, string][]>(() => {
  const s = stations.value.find(({ station }) => station === (chosen.value ?? Station.KUKU))
  if (!s) return []
  const disk: [string, string][] = [
    ['Free space', s.freeBytes == null ? '—' : `${Math.round(s.freeBytes / 1e9)} GB`],
    ['Room for', s.hours == null ? '—' : `≈ ${s.hours.toLocaleString('en-US')} h`],
  ]
  if (!chosen.value) return disk
  const last = s.last ? `${s.last.programme}, ${day(s.last.end)} ${clock(s.last.end, 0)}` : '—'
  return [['Last', last], ['Next', upcoming(s)], ['Format', audioFormat(s.format)], ...disk]
})
</script>
