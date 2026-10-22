<template>
  <radio-card v-if="rows.length" title="Find cuts">
    <p class="mt-0 mb-3.5 text-sm text-ink-soft">
      The cuts found make the clean version right away. You can review and change them here.
    </p>
    <ul class="m-0 grid list-none gap-3.5 p-0">
      <li v-for="row in rows" :key="row.name" class="grid gap-2 border-t border-hairline pt-3.5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <span>{{ row.name }}</span>
          <app-button
            v-if="row.state === ProgrammeCutState.FAILED"
            :aria-label="`Try again: ${row.name}`"
            :loading="asking === row.name"
            @click="find(row.name)"
          >
            Try again
          </app-button>
          <span v-else-if="row.meter" class="text-xl leading-tight font-medium tabular-nums">
            {{ row.meter.percent }}%
          </span>
          <span v-else class="text-ink-soft">Queued</span>
        </div>
        <template v-if="row.meter">
          <div
            role="progressbar"
            :aria-label="row.meter.label"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="row.meter.percent"
            :aria-valuetext="`${row.meter.percent}%, ${row.meter.step}, ${row.meter.left}`"
            class="h-1.5 overflow-hidden rounded-full bg-hairline"
          >
            <div
              class="h-full rounded-full bg-brass transition-[width] duration-700 ease-out motion-reduce:transition-none"
              :style="{ width: `${row.meter.percent}%` }"
            />
          </div>
          <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 text-sm">
            <span>{{ row.meter.step }}</span>
            <span class="text-ink-soft tabular-nums">{{ row.meter.left }}</span>
          </div>
        </template>
        <p v-if="row.state === ProgrammeCutState.WAITING" class="mb-0 text-sm text-ink-soft">
          The search starts once the transcript is ready.
        </p>
        <p v-if="row.state === ProgrammeCutState.QUEUED" class="mb-0 text-sm text-ink-soft">
          {{ row.after ? `Starts after ${row.after}` : 'Starts now' }}
        </p>
        <template v-if="row.state === ProgrammeCutState.FAILED && recording.failure">
          <p class="mb-0 flex items-center gap-1.5 text-sm">
            <radio-icon name="alert" />
            The search stopped at {{ stopped(recording.failure.failedAt) }}
          </p>
          <p class="mb-0 text-sm text-ink-soft">{{ recording.failure.reason }}</p>
        </template>
      </li>
    </ul>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import {
  ProgrammeCutState,
  type ProgrammeCuts,
  type RecordingSummary,
} from '../../models/generated/domain-models'
import { useElapsed } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import { advance, clock, left, stepLabel } from '../../utils/radio'
import AppButton from '../shared/app-button.vue'
import RadioCard from './radio-card.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ recording: RecordingSummary }>()

const queryClient = useQueryClient()
const toast = useToast()
const asking = ref<string | null>(null)
const elapsed = useElapsed(() => props.recording.programmeCuts)

const SHOWN = new Set([
  ProgrammeCutState.FINDING,
  ProgrammeCutState.QUEUED,
  ProgrammeCutState.WAITING,
  ProgrammeCutState.FAILED,
])

const meter = ({ name, progress, transcript }: ProgrammeCuts) => {
  if (progress) {
    const { percent, secondsLeft } = advance(
      progress,
      Math.min(elapsed.value, progress.stepSecondsLeft)
    )
    return {
      label: `${name}: cut search`,
      percent,
      step: stepLabel(progress),
      left: left(secondsLeft),
    }
  }
  if (!transcript) return null
  const written = Math.floor(transcript.writtenSeconds / 60)
  return {
    label: `${name}: transcript`,
    percent: transcript.percent,
    step: `Waiting for the transcript · ${written} / ${Math.round(transcript.totalSeconds / 60)} min written`,
    left: left(transcript.secondsLeft),
  }
}

const rows = computed(() =>
  props.recording.programmeCuts
    .filter(it => SHOWN.has(it.state))
    .map(it => ({ ...it, meter: meter(it) }))
)

const stopped = (at: string) => clock(Date.parse(at) / 1000, 0)

const find = (programme: string) => {
  asking.value = programme
  return radioService
    .findCuts(props.recording.name, { programme })
    .then(() => queryClient.invalidateQueries({ queryKey: ['radio'] }))
    .catch(() => toast.error('Could not start the cut search'))
    .finally(() => (asking.value = null))
}
</script>
