<template>
  <radio-card v-if="station" title="Pidev salvestus">
    <template #aside>
      <radio-badge>Väljas</radio-badge>
    </template>
    <p class="mt-0 mb-2 text-sm text-ink-soft">
      Salvestaks kogu eetri ja hoiaks seda 7 päeva. Siis saaks järele kuulata ka saateid, mida ette
      ei valitud.
    </p>
    <radio-facts :rows="facts" />
    <app-button class="mt-3" disabled aria-describedby="radio-continuous-note">
      Lülita sisse
    </app-button>
    <p id="radio-continuous-note" class="mt-2 mb-0 text-sm text-ink-soft">
      Saab sisse lülitada, kui salvestusi hoitakse
      <span class="whitespace-nowrap">S3-s.</span>
    </p>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRadioStation } from '../../composables/use-radio-recordings'
import AppButton from '../shared/app-button.vue'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'

const { data: station } = useRadioStation()

const estimate = (bytes: number) =>
  `≈ ${(bytes / 1e9).toLocaleString('et', { maximumSignificantDigits: 2 })} GB`

const facts = computed<[string, string][]>(() => {
  const s = station.value!
  return [
    ['Päevas', estimate(s.continuous.dayBytes)],
    ['7 päeva', estimate(s.continuous.weekBytes)],
    ['Vaba ruumi', s.freeBytes == null ? '—' : `${Math.round(s.freeBytes / 1e9)} GB`],
  ]
})
</script>
