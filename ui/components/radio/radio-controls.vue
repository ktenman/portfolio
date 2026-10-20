<template>
  <div
    :class="
      large
        ? 'grid w-full items-center max-md:auto-cols-fr max-md:grid-flow-col max-md:justify-items-center md:grid-cols-[1fr_auto_1fr] md:gap-4'
        : 'flex items-center gap-2'
    "
  >
    <button
      v-if="large"
      type="button"
      data-testid="radio-speed"
      class="h-11 w-12 cursor-pointer rounded-full bg-surface-sunken text-sm font-medium text-ink transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep md:justify-self-start"
      :aria-label="`Kiirus ${player.rate.value}×, muuda`"
      @click="step"
    >
      {{ player.rate.value }}×
    </button>
    <div class="contents" :class="{ 'md:flex md:items-center md:gap-5': large }">
      <button
        v-if="!player.live.value"
        type="button"
        :class="large ? [ROUND, 'flex-col'] : SKIP"
        aria-label="15 s tagasi"
        @click="player.skip(-15)"
      >
        <radio-icon name="back" />
        15
      </button>
      <button
        type="button"
        class="inline-flex shrink-0 cursor-pointer items-center justify-center rounded-full bg-brass text-white shadow-control transition-colors hover:bg-brass-deep focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brass-deep"
        :class="large ? 'size-14 max-md:size-16' : 'size-11 md:size-10'"
        :aria-label="player.playing.value ? 'Peata' : 'Mängi'"
        data-testid="radio-play"
        @click="player.toggle()"
      >
        <radio-icon :name="player.playing.value ? 'pause' : 'play'" />
      </button>
      <button
        v-if="!player.live.value"
        type="button"
        :class="large ? [ROUND, 'flex-col-reverse'] : SKIP"
        aria-label="30 s edasi"
        @click="player.skip(30)"
      >
        30
        <radio-icon name="forward" />
      </button>
    </div>
    <div
      class="flex items-center gap-1.5 text-ink-soft"
      :class="
        large
          ? 'md:h-11 md:justify-self-end md:rounded-full md:bg-surface-sunken md:pr-4 md:pl-1'
          : 'ml-1'
      "
    >
      <button
        type="button"
        :class="{ 'max-md:h-11 max-md:w-12 max-md:bg-surface-sunken': large }"
        class="inline-flex size-11 cursor-pointer items-center justify-center rounded-full transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep md:size-8"
        aria-label="Vaigista"
        :aria-pressed="player.muted.value"
        @click="player.muted.value = !player.muted.value"
      >
        <radio-icon :name="player.muted.value ? 'muted' : 'volume'" />
      </button>
      <input
        v-model.number="player.volume.value"
        type="range"
        class="hidden w-20 cursor-pointer accent-ink md:block"
        min="0"
        max="1"
        step="0.05"
        aria-label="Helitugevus"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { RATES, useRadioPlayer } from '../../composables/use-radio-player'
import RadioIcon from './radio-icon.vue'

defineProps<{ large?: boolean }>()

const SKIP =
  'inline-flex min-h-11 cursor-pointer items-center gap-1.5 rounded-container border border-control-border bg-surface-sunken px-2.5 py-1.5 text-sm font-medium text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep md:min-h-0'

const ROUND =
  'inline-flex size-13 cursor-pointer items-center justify-center rounded-full text-label text-ink transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep'

const player = useRadioPlayer()

const step = () => {
  const next = RATES.indexOf(player.rate.value) + 1
  player.rate.value = RATES[next] ?? RATES[0]!
}
</script>
