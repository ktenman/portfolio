<template>
  <div
    class="grid w-full items-center max-md:auto-cols-fr max-md:grid-flow-col max-md:justify-items-center md:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] md:gap-6"
  >
    <div class="flex min-w-0 items-center gap-4 md:justify-end">
      <slot />
      <button
        v-if="!player.live.value"
        type="button"
        data-testid="radio-speed"
        :class="SUNKEN"
        class="text-sm font-medium"
        :aria-label="`Kiirus ${player.rate.value}×, muuda`"
        @click="step"
      >
        {{ player.rate.value }}×
      </button>
    </div>
    <div class="contents md:flex md:items-center md:gap-5">
      <button
        v-if="!player.live.value"
        type="button"
        :class="[ROUND, large ? 'size-13' : 'size-12', 'flex-col']"
        aria-label="15 s tagasi"
        @click="player.skip(-15)"
      >
        <radio-icon name="back" />
        15
      </button>
      <button
        type="button"
        class="inline-flex shrink-0 cursor-pointer items-center justify-center rounded-full bg-brass text-white shadow-control transition-colors hover:bg-brass-deep focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brass-deep"
        :class="large ? 'size-14 max-md:size-16' : 'size-14 md:size-12'"
        :aria-label="player.playing.value ? 'Peata' : 'Mängi'"
        data-testid="radio-play"
        @click="player.toggle()"
      >
        <radio-icon :name="player.playing.value ? 'pause' : 'play'" />
      </button>
      <button
        v-if="!player.live.value"
        type="button"
        :class="[ROUND, large ? 'size-13' : 'size-12', 'flex-col-reverse']"
        aria-label="30 s edasi"
        @click="player.skip(30)"
      >
        30
        <radio-icon name="forward" />
      </button>
    </div>
    <div class="flex items-center gap-2.5 md:justify-self-start">
      <button
        type="button"
        :class="SUNKEN"
        aria-label="Vaigista"
        :aria-pressed="player.muted.value"
        @click="player.muted.value = !player.muted.value"
      >
        <radio-icon :name="player.muted.value ? 'muted' : 'volume'" />
      </button>
      <input
        v-model.number="player.volume.value"
        type="range"
        class="volume-range hidden w-22 md:block"
        :class="{ muted: player.muted.value }"
        :style="{ '--level': `${player.volume.value * 100}%` }"
        min="0"
        max="1"
        step="0.05"
        aria-label="Helitugevus"
        :aria-valuetext="`${Math.round(player.volume.value * 100)}%`"
        @input="player.muted.value = false"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { RATES, useRadioPlayer } from '../../composables/use-radio-player'
import RadioIcon from './radio-icon.vue'

defineProps<{ large?: boolean }>()

const SUNKEN =
  'inline-flex h-11 w-12 shrink-0 cursor-pointer items-center justify-center rounded-full bg-surface-sunken text-ink transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep'

const ROUND =
  'inline-flex shrink-0 cursor-pointer items-center justify-center rounded-full text-label text-ink transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep'

const player = useRadioPlayer()

const step = () => {
  const next = RATES.indexOf(player.rate.value) + 1
  player.rate.value = RATES[next] ?? RATES[0]!
}
</script>
