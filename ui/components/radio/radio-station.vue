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
    <radio-card v-if="station?.onAir" title="On air">
      <template #aside><radio-badge tone="live">Live</radio-badge></template>
      <p class="mb-0 font-semibold">{{ station.onAir.name }}</p>
      <p class="mb-3 text-sm text-body-secondary">{{ airing(station.onAir) }}</p>
      <button
        type="button"
        :class="[LINK, 'cursor-pointer']"
        @click="on ? player.toggle() : player.listen()"
      >
        <radio-icon :name="on ? 'pause' : 'play'" />
        {{ on ? 'Pause' : 'Listen live' }}
      </button>
      <p v-if="station.next" class="mt-3 mb-0 text-sm text-body-secondary">
        Next: {{ station.next.name }} at {{ clock(station.next.start, 0) }}
      </p>
    </radio-card>
  </aside>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { StationAiring } from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioStation } from '../../composables/use-radio-recordings'
import { audioFormat, clock, day, LINK, range, RECORDER_STATES } from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'
import RadioIcon from './radio-icon.vue'

const { data: station } = useRadioStation()
const player = useRadioPlayer()
const on = computed(() => player.live.value && player.playing.value)

const airing = (a: StationAiring) =>
  `${range(a.start, 0, a.end - a.start)} · ${a.recorded ? 'being recorded' : 'not being recorded'}`

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
