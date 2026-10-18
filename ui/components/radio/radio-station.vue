<template>
  <aside class="flex flex-col gap-4" aria-label="Salvestaja ja eeter">
    <radio-card title="Salvestaja">
      <radio-facts :rows="recorder" />
    </radio-card>
    <radio-card v-if="station?.onAir" title="Eetris">
      <template #aside><radio-badge tone="live">Otse</radio-badge></template>
      <p class="mb-0 font-semibold">{{ station.onAir.name }}</p>
      <p class="mb-3 text-sm text-body-secondary">{{ airing(station.onAir) }}</p>
      <a :class="LINK" href="https://kuku.pleier.ee" target="_blank" rel="noopener">Kuula otse</a>
      <p v-if="station.next" class="mt-3 mb-0 text-sm text-body-secondary">
        Järgmisena {{ clock(station.next.start, 0) }} {{ station.next.name }}
      </p>
    </radio-card>
  </aside>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { StationAiring } from '../../models/generated/domain-models'
import { useRadioStation } from '../../composables/use-radio-recordings'
import { clock, day, LINK, range } from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'

const { data: station } = useRadioStation()

const airing = (a: StationAiring) =>
  `${range(a.start, 0, a.end - a.start)} · ${a.recorded ? 'salvestatakse' : 'ei salvestata'}`

const recorder = computed<[string, string][]>(() => {
  const s = station.value
  if (!s) return []
  const next = s.capture
    ? `${s.capture.programmes.join(', ')}, ${day(s.capture.start)} ${clock(s.capture.start, 0)}`
    : 'Pole valitud'
  return [
    ['Järgmine', next],
    ['Vaba ruumi', `${Math.round(s.freeBytes / 1e9)} GB`],
    ['Jätkub', `≈ ${s.hours.toLocaleString('et')} tundi`],
  ]
})
</script>
