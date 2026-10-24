<template>
  <section v-if="station?.onAir" :class="aside ? 'card mb-0 overflow-hidden' : 'mt-8'">
    <div
      class="flex items-baseline gap-2.5"
      :class="aside ? 'justify-between px-5 pt-5 pb-1.5' : 'mb-3'"
    >
      <h3 class="mb-0 text-heading">On air</h3>
      <radio-badge tone="live">Live</radio-badge>
    </div>
    <div :class="{ 'card mb-0 overflow-hidden': !aside }">
      <div class="flex items-center gap-3.5 px-5 py-3.5">
        <span
          class="grid h-13 w-15 shrink-0 content-center justify-items-center gap-1 rounded-container bg-notice-wash text-notice"
        >
          <span class="text-label leading-none font-medium">Now</span>
          <span class="text-lg leading-none font-semibold tabular-nums">
            {{ clock(station.onAir.start, 0) }}
          </span>
        </span>
        <span class="grid min-w-0 grow gap-0.5">
          <span class="font-semibold">{{ station.onAir.name }}</span>
          <span class="text-sm text-ink-soft tabular-nums">
            until {{ clock(station.onAir.end, 0) }} ·
            <span class="whitespace-nowrap">
              {{ station.onAir.recorded ? 'recording' : 'not recorded' }}
            </span>
          </span>
        </span>
        <button
          type="button"
          :class="[PLAY, 'size-11']"
          :aria-label="on ? 'Pause' : 'Listen live'"
          @click="on ? player.toggle() : player.listen()"
        >
          <radio-icon :name="on ? 'pause' : 'play'" />
        </button>
      </div>
      <div class="h-0.5 bg-surface-sunken" aria-hidden="true">
        <div
          data-testid="radio-on-air-progress"
          class="h-full bg-brass"
          :style="{ width: `${progress}%` }"
        />
      </div>
      <div v-if="station.next" class="flex items-center gap-3.5 px-5 py-3.5">
        <span
          class="grid h-13 w-15 shrink-0 content-center justify-items-center gap-1 rounded-container bg-surface-sunken"
        >
          <span class="text-label leading-none font-medium text-ink-soft">Next</span>
          <span class="text-lg leading-none font-semibold tabular-nums">
            {{ clock(station.next.start, 0) }}
          </span>
        </span>
        <span class="grid min-w-0 grow gap-0.5">
          <span class="font-semibold">{{ station.next.name }}</span>
          <span class="text-sm text-ink-soft tabular-nums">
            in {{ minutes(station.next.start - seconds) }}
          </span>
        </span>
      </div>
    </div>
  </section>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useIntervalFn, useNow } from '@vueuse/core'
import { useRadioPlayer } from '../../composables/use-radio-player'
import { useRadioStation } from '../../composables/use-radio-recordings'
import { clock, minutes, PLAY } from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioIcon from './radio-icon.vue'

defineProps<{ aside?: boolean }>()

const { data: station } = useRadioStation()
const player = useRadioPlayer()
const on = computed(() => player.live.value && player.playing.value)
const now = useNow({ scheduler: tick => useIntervalFn(tick, 60_000) })
const seconds = computed(() => now.value.getTime() / 1000)

const progress = computed(() => {
  const airing = station.value?.onAir
  if (!airing) return 0
  const share = (seconds.value - airing.start) / (airing.end - airing.start)
  return Math.round(Math.min(1, Math.max(0, share)) * 100)
})
</script>
