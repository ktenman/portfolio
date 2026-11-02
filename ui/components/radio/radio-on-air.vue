<template>
  <section v-if="live.length" :class="aside ? 'card mb-0 overflow-hidden' : 'mt-8'">
    <h3 class="text-heading" :class="aside ? 'mb-0 px-5 pt-5 pb-1.5' : 'mb-3'">On air</h3>
    <div :class="{ 'card mb-0 overflow-hidden': !aside }">
      <template v-for="{ station, onAir, next } in live" :key="station">
        <div class="flex items-center gap-3.5 px-5 py-3">
          <button
            type="button"
            :class="[PLAY, 'size-11 [&>svg]:size-4.5']"
            :aria-label="`${on(station) ? 'Pause' : 'Listen live'}: ${STATIONS[station]}`"
            @click="on(station) ? player.toggle() : player.listen(station)"
          >
            <radio-icon :name="on(station) ? 'pause' : 'play'" />
          </button>
          <span class="grid min-w-0 grow gap-0.5">
            <span class="font-semibold">{{ onAir.name }}</span>
            <span class="text-sm text-ink-soft tabular-nums">
              {{ STATIONS[station] }} · {{ range(onAir.start, 0, onAir.end - onAir.start) }}
              <span class="whitespace-nowrap">
                {{ `· ${onAir.recorded ? 'recording' : 'not recorded'}` }}
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
        <p v-if="next" class="mb-0 px-5 pt-2 pb-3 text-sm text-ink-soft tabular-nums">
          Next {{ clock(next.start, 0) }} · {{ next.name }}
        </p>
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
import { clock, PLAY, range, STATIONS } from '../../utils/radio'
import RadioIcon from './radio-icon.vue'

defineProps<{ aside?: boolean }>()

const stations = useRailStations()
const player = useRadioPlayer()
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })

const live = computed(() =>
  stations.value.flatMap(({ station, onAir, next }) => (onAir ? [{ station, onAir, next }] : []))
)

const on = (station: Station) => player.tuned.value === station && player.playing.value

const progress = ({ start, end }: StationAiring) => {
  const share = (now.value.getTime() / 1000 - start) / (end - start)
  return Math.round(Math.min(1, Math.max(0, share)) * 100)
}
</script>
