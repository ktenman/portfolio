<template>
  <aside class="flex flex-col gap-4" aria-label="Salvestaja ja eeter">
    <radio-card title="Salvestaja">
      <template v-if="station" #aside>
        <radio-badge :tone="RECORDER_STATES[station.recorder].tone">
          {{ RECORDER_STATES[station.recorder].label }}
        </radio-badge>
      </template>
      <radio-facts :rows="recorder" />
    </radio-card>
    <radio-card v-if="station?.onAir" title="Eetris">
      <template #aside><radio-badge tone="live">Otse</radio-badge></template>
      <p class="mb-0 font-semibold">{{ station.onAir.name }}</p>
      <p class="mb-3 text-sm text-body-secondary">{{ airing(station.onAir) }}</p>
      <button
        type="button"
        :class="[LINK, 'cursor-pointer']"
        @click="on ? player.toggle() : player.listen()"
      >
        <radio-icon :name="on ? 'pause' : 'play'" />
        {{ on ? 'Peata' : 'Kuula otse' }}
      </button>
      <p v-if="station.next" class="mt-3 mb-0 text-sm text-body-secondary">
        Järgmisena {{ clock(station.next.start, 0) }} {{ station.next.name }}
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
  `${range(a.start, 0, a.end - a.start)} · ${a.recorded ? 'salvestatakse' : 'ei salvestata'}`

const recorder = computed<[string, string][]>(() => {
  const s = station.value
  if (!s) return []
  const next = s.capture
    ? `${s.capture.programmes.join(', ')}, ${day(s.capture.slot.start)} ${clock(s.capture.slot.start, 0)}`
    : 'Pole valitud'
  const last = s.last ? `${s.last.programme}, ${day(s.last.end)} ${clock(s.last.end, 0)}` : '—'
  return [
    ['Viimane', last],
    ['Järgmine', next],
    ['Vorming', audioFormat(s.format)],
    ['Vaba ruumi', s.freeBytes == null ? '—' : `${Math.round(s.freeBytes / 1e9)} GB`],
    ['Jätkub', s.hours == null ? '—' : `≈ ${s.hours.toLocaleString('et')} tundi`],
  ]
})
</script>
