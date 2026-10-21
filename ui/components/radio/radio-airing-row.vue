<template>
  <div
    class="grid grid-cols-[3.25rem_minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1.5 px-3 py-3 md:grid-cols-[3.75rem_minmax(0,1fr)_3.5rem_9.5rem_7.5rem] md:px-4"
    :class="{ 'text-ink-soft': airing.state === AiringState.MISSED }"
    data-testid="radio-airing"
  >
    <span class="row-span-3 self-start font-semibold tabular-nums md:row-span-1 md:self-center">
      {{ clock(airing.start, 0) }}
    </span>
    <div class="min-w-0">
      <span class="block font-semibold">{{ airing.name }}</span>
      <span v-if="note" class="block text-sm text-body-secondary">{{ note }}</span>
    </div>
    <span class="hidden text-ink-soft tabular-nums md:block">
      {{ airLength(airing.end - airing.start) }}
    </span>
    <span class="col-start-2 max-md:empty:hidden md:col-start-auto">
      <radio-badge v-if="badge" :tone="badge.tone">{{ badge.label }}</radio-badge>
      <span v-else-if="airing.state === AiringState.ARCHIVED" class="text-sm text-ink-soft">
        Originaal arhiivis
      </span>
    </span>
    <span
      class="col-start-3 row-span-3 row-start-1 justify-self-end md:col-start-auto md:row-span-1 md:row-start-auto"
    >
      <button
        v-if="action"
        type="button"
        :class="[
          LINK,
          'cursor-pointer disabled:cursor-default disabled:opacity-50',
          action.quiet ? 'bg-surface-sunken text-ink-soft' : 'bg-surface',
        ]"
        :disabled="!action.run"
        :aria-label="`${action.label}: ${airing.name} ${clock(airing.start, 0)}`"
        @click="action.run?.()"
      >
        <radio-icon v-if="airing.state === AiringState.RECORDED" name="play" />
        {{ action.label }}
      </button>
    </span>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useQueryClient } from '@tanstack/vue-query'
import {
  type AiringRecording,
  AiringState,
  type DayAiring,
} from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioRecordings } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import { AIRING_STATES, airingNote, airLength, clock, coversEnd, LINK } from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ airing: DayAiring }>()
const player = useRadioPlayer()
const { data: recordings } = useRadioRecordings()
const queryClient = useQueryClient()
const router = useRouter()
const toast = useToast()

const note = computed(() => airingNote(props.airing))
const badge = computed(() => AIRING_STATES[props.airing.state])

const listen = (recording: AiringRecording | null | undefined) => {
  const summary = recordings.value?.find(r => r.name === recording?.name)
  if (!recording || !summary) return undefined
  return () => {
    player.load(summary)
    player.playFrom(recording.from - recording.start)
    void router.push(`/radio/${summary.name}`)
  }
}

const record = (value: boolean) => () =>
  radioService
    .record(props.airing.id, { record: value })
    .then(() =>
      Promise.all(
        ['radio-day', 'radio-station'].map(key =>
          queryClient.invalidateQueries({ queryKey: [key] })
        )
      )
    )
    .catch(() => toast.error('Valikut ei saanud salvestada'))

const action = computed(() => {
  const airing = props.airing
  switch (airing.state) {
    case AiringState.RECORDED:
      return { label: 'Kuula', run: listen(airing.recording) }
    case AiringState.PARTIAL:
      return {
        label: coversEnd(airing) ? 'Kuula lõppu' : 'Kuula',
        run: listen(airing.recording),
        quiet: true,
      }
    case AiringState.ARCHIVED:
      return { label: 'Kuula', run: listen(airing.original?.recording), quiet: true }
    case AiringState.ON_AIR:
    case AiringState.RECORDING:
      return player.live.value && player.playing.value
        ? { label: 'Peata', run: player.toggle, quiet: true }
        : { label: 'Kuula otse', run: player.listen, quiet: true }
    case AiringState.OPEN:
      return { label: 'Salvesta', run: record(true) }
    case AiringState.PLANNED:
      return airing.once ? { label: 'Tühista', run: record(false) } : null
    default:
      return null
  }
})
</script>
