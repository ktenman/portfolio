<template>
  <radio-card v-if="open.length" title="Leia lõiked">
    <p class="mt-0 mb-3 text-sm text-ink-soft">
      Leitud lõigetest tehakse kohe puhastatud versioon. Lõikeid saad siin üle vaadata ja muuta.
    </p>
    <ul class="m-0 grid list-none gap-2 p-0">
      <li
        v-for="programme in open"
        :key="programme.name"
        class="flex flex-wrap items-center justify-between gap-2"
      >
        <span>{{ programme.name }}</span>
        <span v-if="programme.state === ProgrammeCutState.FINDING" class="text-sm text-ink-soft">
          Otsin lõikeid…
        </span>
        <app-button
          v-else
          :aria-label="`Leia lõiked: ${programme.name}`"
          :loading="asking === programme.name"
          @click="find(programme.name)"
        >
          Leia lõiked
        </app-button>
      </li>
    </ul>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { ProgrammeCutState, type RecordingSummary } from '../../models/generated/domain-models'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import AppButton from '../shared/app-button.vue'
import RadioCard from './radio-card.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const queryClient = useQueryClient()
const toast = useToast()
const asking = ref<string | null>(null)

const open = computed(() =>
  props.recording.programmeCuts.filter(it => it.state !== ProgrammeCutState.FOUND)
)

const find = (programme: string) => {
  asking.value = programme
  return radioService
    .findCuts(props.recording.name, { programme })
    .then(() => queryClient.invalidateQueries({ queryKey: ['radio'] }))
    .catch(() => toast.error('Lõikeid ei saanud otsima panna'))
    .finally(() => (asking.value = null))
}
</script>
