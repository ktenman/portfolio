<template>
  <div class="flex items-center gap-2">
    <button
      v-if="!player.live.value"
      type="button"
      :class="SKIP"
      aria-label="15 s tagasi"
      @click="player.skip(-15)"
    >
      <radio-icon name="back" />
      15
    </button>
    <button
      type="button"
      class="inline-flex shrink-0 cursor-pointer items-center justify-center rounded-full bg-brass text-white shadow-control transition-colors hover:bg-brass-deep focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brass-deep"
      :class="large ? 'size-13' : 'size-11 md:size-10'"
      :aria-label="player.playing.value ? 'Peata' : 'Mängi'"
      data-testid="radio-play"
      @click="player.toggle()"
    >
      <radio-icon :name="player.playing.value ? 'pause' : 'play'" />
    </button>
    <button
      v-if="!player.live.value"
      type="button"
      :class="SKIP"
      aria-label="30 s edasi"
      @click="player.skip(30)"
    >
      30
      <radio-icon name="forward" />
    </button>
    <div class="ml-1 flex items-center gap-1.5 text-ink-soft">
      <button
        type="button"
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
        class="hidden w-20 cursor-pointer accent-brass md:block"
        min="0"
        max="1"
        step="0.05"
        aria-label="Helitugevus"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { useRadioPlayer } from '../../composables/use-radio-player'
import RadioIcon from './radio-icon.vue'

defineProps<{ large?: boolean }>()

const SKIP =
  'inline-flex min-h-11 cursor-pointer items-center gap-1.5 rounded-container border border-control-border bg-surface-sunken px-2.5 py-1.5 text-sm font-medium text-ink-soft transition-colors hover:bg-brass-wash hover:text-brass-deep focus-visible:outline-2 focus-visible:outline-brass-deep md:min-h-0'

const player = useRadioPlayer()
</script>
