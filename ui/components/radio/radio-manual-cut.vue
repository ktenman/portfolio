<template>
  <radio-card title="Add a cut manually">
    <form class="grid gap-3" @submit.prevent="add">
      <div class="grid gap-3 sm:grid-cols-2">
        <div v-for="end in ENDS" :key="end.key" class="grid gap-1">
          <label :for="`radio-manual-${end.key}`" class="text-sm text-ink-soft">
            {{ end.label }}
          </label>
          <div class="flex gap-2">
            <input
              :id="`radio-manual-${end.key}`"
              v-model="times[end.key]"
              inputmode="numeric"
              placeholder="hh:mm:ss"
              size="8"
              class="min-w-0 grow rounded-container border border-control-border bg-surface px-3 py-1.5 text-ink tabular-nums shadow-control focus-visible:outline-2 focus-visible:outline-brass-deep"
            />
            <app-button
              class="whitespace-nowrap"
              :disabled="here === null"
              :aria-label="`${end.label}: current position`"
              @click="mark(end.key)"
            >
              Current position
            </app-button>
          </div>
        </div>
      </div>
      <div class="flex flex-wrap gap-2">
        <app-button :disabled="!span" @click="span && player.preview(span.start, span.end)">
          <radio-icon name="play" class="me-1.5" />
          Listen to the transition
        </app-button>
        <app-button type="submit" variant="primary" :disabled="!span" :loading="adding">
          Add cut
        </app-button>
      </div>
    </form>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed, reactive, ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import type { RecordingSummary } from '../../models/generated/domain-models'
import { useRadioDecisions } from '../../composables/use-radio-decisions'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import { clock, offset } from '../../utils/radio'
import AppButton from '../shared/app-button.vue'
import RadioCard from './radio-card.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const ENDS = [
  { key: 'start', label: 'Start' },
  { key: 'end', label: 'End' },
] as const

const player = useRadioPlayer()
const decisions = useRadioDecisions(() => props.recording)
const queryClient = useQueryClient()
const toast = useToast()
const times = reactive({ start: '', end: '' })
const adding = ref(false)

const here = computed(() =>
  player.recording.value?.name === props.recording.name ? player.recordingSeconds.value : null
)

const span = computed(() => {
  const { startedAt, seconds } = props.recording.status
  const start = offset(startedAt, times.start)
  const end = offset(startedAt, times.end)
  if (start === null || end === null || start >= end || end > seconds) return null
  return { start, end }
})

const mark = (key: 'start' | 'end') => {
  if (here.value !== null) times[key] = clock(props.recording.status.startedAt, here.value, true)
}

const add = async () => {
  if (!span.value) return
  adding.value = true
  await radioService
    .addCut(props.recording.name, span.value)
    .then(() => {
      decisions.choose(props.recording.cuts.length, true)
      Object.assign(times, { start: '', end: '' })
      toast.success('Cut added')
      return queryClient.invalidateQueries({ queryKey: ['radio'] })
    })
    .catch(() => toast.error('Could not add the cut: does it overlap another cut?'))
    .finally(() => (adding.value = false))
}
</script>
