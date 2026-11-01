<template>
  <section v-if="live.length" :class="aside ? 'card mb-0 overflow-hidden' : 'mt-8'">
    <h3 class="text-heading" :class="aside ? 'mb-0 px-5 pt-5 pb-1.5' : 'mb-3'">On air</h3>
    <div :class="{ 'card mb-0 overflow-hidden': !aside }">
      <template v-for="{ station, onAir, next } in live" :key="station">
        <div class="flex items-center gap-3.5 px-5 py-3.5">
          <button
            type="button"
            :class="[PLAY, 'mx-1 size-13 [&>svg]:size-5']"
            :aria-label="`${on(station) ? 'Pause' : 'Listen live'}: ${STATIONS[station]}`"
            @click="on(station) ? player.toggle() : player.listen(station)"
          >
            <radio-icon :name="on(station) ? 'pause' : 'play'" />
          </button>
          <span class="grid min-w-0 grow gap-0.5">
            <span class="font-semibold">{{ onAir.name }}</span>
            <span class="text-sm text-ink-soft tabular-nums">
              {{ STATIONS[station] }} · {{ range(onAir.start, 0, onAir.end - onAir.start) }} ·
              <span class="whitespace-nowrap">
                {{ onAir.recorded ? 'recording' : 'not recorded' }}
              </span>
            </span>
          </span>
        </div>
        <div class="h-0.5 bg-surface-sunken" aria-hidden="true">
          <div
            data-testid="radio-on-air-progress"
            class="h-full bg-brass"
            :style="{ width: `${progress(onAir)}%` }"
          />
        </div>
        <div v-if="next" class="flex items-center gap-3.5 px-5 py-3.5">
          <span
            class="grid h-13 w-15 shrink-0 content-center justify-items-center gap-1 rounded-container bg-surface-sunken"
          >
            <span class="text-label leading-none font-medium text-ink-soft">Next</span>
            <span class="text-lg leading-none font-semibold tabular-nums">
              {{ clock(next.start, 0) }}
            </span>
          </span>
          <span class="grid min-w-0 grow gap-0.5">
            <span class="font-semibold">{{ next.name }}</span>
            <span class="text-sm text-ink-soft tabular-nums">
              in {{ minutes(next.start - seconds) }}
            </span>
          </span>
        </div>
      </template>
    </div>
  </section>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useIntervalFn, useNow } from '@vueuse/core'
import type { Station, StationAiring } from '../../models/generated/domain-models'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRailStations } from '../../composables/use-radio-recordings'
import { clock, minutes, PLAY, range, STATIONS } from '../../utils/radio'
import RadioIcon from './radio-icon.vue'

defineProps<{ aside?: boolean }>()

const stations = useRailStations()
const player = useRadioPlayer()
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })
const seconds = computed(() => now.value.getTime() / 1000)

const live = computed(() =>
  stations.value.flatMap(({ station, onAir, next }) => (onAir ? [{ station, onAir, next }] : []))
)

const on = (station: Station) => player.tuned.value === station && player.playing.value

const progress = ({ start, end }: StationAiring) => {
  const share = (seconds.value - start) / (end - start)
  return Math.round(Math.min(1, Math.max(0, share)) * 100)
}
</script>
