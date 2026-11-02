<template>
  <div
    class="relative grid grid-cols-[3.25rem_minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1.5 px-3 py-3 md:grid-cols-[60px_72px_minmax(0,1fr)_56px_152px_120px] md:px-4"
    :class="{ 'text-ink-soft': airing.state === AiringState.MISSED }"
    data-testid="radio-airing"
  >
    <span class="row-span-3 self-start font-semibold tabular-nums md:row-span-1 md:self-center">
      {{ clock(airing.start, 0) }}
    </span>
    <span class="hidden text-sm text-ink-soft md:block" data-testid="radio-airing-station">
      {{ station }}
    </span>
    <div class="min-w-0">
      <router-link
        v-if="page"
        :to="page"
        :class="[ROW_LINK, 'block hover:underline']"
        :aria-label="`${airing.name} ${clock(airing.start, 0)} · ${station}`"
      >
        {{ airing.name }}
      </router-link>
      <span v-else class="block font-semibold">{{ airing.name }}</span>
      <span class="block text-sm text-body-secondary md:hidden">
        {{ [station, note].filter(Boolean).join(' · ') }}
      </span>
      <span v-if="note" class="hidden text-sm text-body-secondary md:block">{{ note }}</span>
    </div>
    <span class="hidden text-ink-soft tabular-nums md:block">
      {{ airLength(airing.end - airing.start) }}
    </span>
    <span class="col-start-2 max-md:empty:hidden md:col-start-auto">
      <radio-badge v-if="badge" :tone="badge.tone">{{ badge.label }}</radio-badge>
      <span v-else-if="airing.state === AiringState.ARCHIVED" class="text-sm text-ink-soft">
        Original archived
      </span>
    </span>
    <span
      class="col-start-3 row-span-3 row-start-1 justify-self-end md:col-start-auto md:row-span-1 md:row-start-auto"
    >
      <button
        v-if="action"
        type="button"
        :class="[
          action.icon
            ? [PLAY, 'size-11 md:size-9']
            : [LINK, action.quiet ? 'bg-surface-sunken text-ink-soft' : 'bg-surface'],
          'relative z-10 cursor-pointer disabled:cursor-default disabled:opacity-50',
        ]"
        :disabled="!action.run"
        :aria-label="`${action.label}: ${airing.name} ${clock(airing.start, 0)} · ${station}`"
        @click="action.run?.()"
      >
        <radio-icon v-if="action.icon" :name="action.icon" />
        <template v-else>{{ action.label }}</template>
      </button>
    </span>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useQueryClient } from '@tanstack/vue-query'
import { AiringState, type DayAiring } from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioRecordings } from '../../composables/use-radio-recordings'
import { useToast } from '../../composables/use-toast'
import { radioService } from '../../services/api'
import {
  AIRING_STATES,
  airingNote,
  airLength,
  clock,
  coversEnd,
  LINK,
  PLAY,
  ROW_LINK,
  STATIONS,
} from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ airing: DayAiring }>()
const player = useRadioPlayer()
const { data: recordings } = useRadioRecordings()
const queryClient = useQueryClient()
const router = useRouter()
const toast = useToast()

const station = computed(() => STATIONS[props.airing.station])
const note = computed(() => airingNote(props.airing))
const badge = computed(() => AIRING_STATES[props.airing.state])

const recording = computed(() =>
  props.airing.state === AiringState.ARCHIVED
    ? props.airing.original?.recording
    : props.airing.recording
)
const summary = computed(() => {
  const name = recording.value?.name
  return name ? recordings.value?.find(r => r.name === name) : undefined
})
const page = computed(() => summary.value && `/radio/${summary.value.name}`)

const listen = () => {
  const heard = recording.value
  const found = summary.value
  if (!heard || !found) return undefined
  return () => {
    player.load(found)
    player.playFrom(heard.from - heard.start)
    void router.push(`/radio/${found.name}`)
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
    .catch(() => toast.error('Could not save the choice'))

const action = computed(() => {
  const airing = props.airing
  switch (airing.state) {
    case AiringState.RECORDED:
      return { label: 'Listen', run: listen(), icon: 'play' as const }
    case AiringState.PARTIAL:
      return {
        label: coversEnd(airing) ? 'Hear the end' : 'Listen',
        run: listen(),
        quiet: true,
      }
    case AiringState.ARCHIVED:
      return { label: 'Listen', run: listen(), quiet: true }
    case AiringState.ON_AIR:
    case AiringState.RECORDING:
      return player.tuned.value === airing.station && player.playing.value
        ? { label: 'Pause', run: player.toggle, icon: 'pause' as const }
        : {
            label: 'Listen live',
            run: () => player.listen(airing.station),
            icon: 'play' as const,
          }
    case AiringState.OPEN:
      return { label: 'Record', run: record(true) }
    case AiringState.PLANNED:
      return airing.once ? { label: 'Cancel', run: record(false) } : null
    default:
      return null
  }
})
</script>
