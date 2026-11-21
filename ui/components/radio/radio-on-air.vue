<template>
  <component
    :is="aside ? 'aside' : 'section'"
    v-if="live.length || facts.length || off"
    :aria-label="aside ? title : undefined"
    :class="{ 'card mb-0 overflow-hidden': aside }"
  >
    <h3 class="text-heading" :class="aside ? 'mb-0 px-5 pt-5 pb-1.5' : 'mb-3'">{{ title }}</h3>
    <div :class="aside ? 'pb-2' : 'card mb-0 overflow-hidden py-2'">
      <div
        v-for="{ station, onAir } in live"
        :key="station"
        class="flex items-center gap-3.5 px-5 py-2"
      >
        <button
          type="button"
          :class="[on(station) ? LOADED : LISTEN, 'relative size-11 [&>svg]:size-5']"
          :aria-label="`${on(station) ? 'Pause' : 'Listen live'}: ${STATIONS[station]}`"
          @click="on(station) ? player.toggle() : player.listen(station)"
        >
          <radio-ring :share="share(onAir)" />
          <radio-icon :name="on(station) ? 'pause' : 'play'" />
        </button>
        <span class="grid min-w-0 grow gap-0.5">
          <span class="truncate font-semibold">{{ onAir.name }}</span>
          <span
            class="flex items-baseline justify-between gap-2 text-sm text-ink-soft tabular-nums"
          >
            <span>{{ STATIONS[station] }} · until {{ clock(onAir.end, 0) }}</span>
            <span
              v-if="onAir.recorded"
              role="img"
              :aria-label="RECORDING.label"
              :title="RECORDING.label"
              :class="[RECORDING.mark, 'text-xs font-semibold tracking-wide uppercase']"
            >
              Rec
            </span>
          </span>
        </span>
      </div>
      <p v-if="off" class="mb-0 px-5 py-2 text-sm text-loss-deep">Not running: {{ off }}</p>
      <radio-facts
        v-if="facts.length"
        :rows="facts"
        class="mx-5 tabular-nums"
        :class="{ 'mt-2 border-t border-hairline': live.length || off }"
      />
    </div>
  </component>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { clamp, useIntervalFn, useNow } from '@vueuse/core'
import {
  AiringState,
  RecorderState,
  Station,
  type StationAiring,
} from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRailStations, useStationFilter } from '../../composables/use-radio-recordings'
import { AIRING_LIVE, clock, LISTEN, LOADED, STATIONS } from '../../utils/radio'
import RadioFacts from './radio-facts.vue'
import RadioIcon from './radio-icon.vue'
import RadioRing from './radio-ring.vue'

defineProps<{ aside?: boolean }>()

const RECORDING = AIRING_LIVE[AiringState.RECORDING]!

const stations = useRailStations()
const chosen = useStationFilter()
const player = useRadioPlayer()
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })

const title = computed(() => (chosen.value ? 'Station' : 'Stations'))

const live = computed(() =>
  stations.value.flatMap(({ station, onAir }) => (onAir ? [{ station, onAir }] : []))
)

const off = computed(() =>
  stations.value
    .filter(({ recorder }) => recorder === RecorderState.OFF)
    .map(({ station }) => STATIONS[station])
    .join(', ')
)

const facts = computed<[string, string][]>(() => {
  const s = stations.value.find(({ station }) => station === Station.KUKU) ?? stations.value[0]
  if (s?.freeBytes == null) return []
  const free: [string, string] = ['Free space', `${Math.round(s.freeBytes / 1e9)} GB`]
  return s.hours == null ? [free] : [free, ['Room for', `≈ ${s.hours.toLocaleString('en-US')} h`]]
})

const on = (station: Station) => player.tuned.value === station && player.playing.value

const share = ({ start, end }: StationAiring) =>
  clamp((now.value.getTime() / 1000 - start) / (end - start), 0, 1)
</script>
