<template>
  <radio-card v-if="station" title="Pidev salvestus">
    <template #aside>
      <radio-badge :tone="on ? 'live' : 'plain'">{{ on ? 'Sees' : 'Väljas' }}</radio-badge>
    </template>
    <p v-if="on" class="mt-0 mb-2 text-sm text-ink-soft">
      Salvestab kogu eetrit ja hoiab seda 7 päeva. Järelkuulamises saab kuulata ka saateid, mida
      ette ei valitud.
    </p>
    <p v-else class="mt-0 mb-2 text-sm text-ink-soft">
      Salvestaks kogu eetri ja hoiaks seda 7 päeva. Siis saaks järele kuulata ka saateid, mida ette
      ei valitud.
    </p>
    <radio-facts :rows="facts" />
    <app-button class="mt-3" :disabled="saving" @click="toggle">
      {{ on ? 'Lülita välja' : 'Lülita sisse' }}
    </app-button>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useRadioStation } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import AppButton from '../shared/app-button.vue'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'
import RadioFacts from './radio-facts.vue'

const { data: station } = useRadioStation()
const queryClient = useQueryClient()
const toast = useToast()
const saving = ref(false)

const on = computed(() => station.value?.continuous.on ?? false)

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

const toggle = () => {
  saving.value = true
  radioService
    .continuous({ on: !on.value })
    .then(() => queryClient.invalidateQueries({ queryKey: ['radio-station'] }))
    .catch(() => toast.error('Pidevat salvestust ei saanud ümber lülitada'))
    .finally(() => (saving.value = false))
}
</script>
