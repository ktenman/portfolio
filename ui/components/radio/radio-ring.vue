<template>
  <span
    class="pointer-events-none absolute inset-[1.25px] transition-opacity starting:opacity-0"
    aria-hidden="true"
    data-testid="radio-ring"
  >
    <svg
      class="block size-full overflow-visible"
      fill="none"
      stroke-width="2.5"
      stroke-linecap="round"
    >
      <rect width="100%" height="100%" :class="[FOLLOW, shape, 'stroke-hairline']" />
      <rect
        width="100%"
        height="100%"
        pathLength="100"
        :class="[FOLLOW, shape, 'stroke-brass', { invisible: !player.progress.value }]"
        :stroke-dasharray="dash"
      />
    </svg>
  </span>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRadioPlayer } from '../../composables/use-radio-player'

const FOLLOW =
  '[transition:rx_var(--morph),stroke-dashoffset_var(--morph)] starting:[rx:50%] starting:[stroke-dashoffset:0]'

const CIRCLE = '[rx:50%]'

const SQUARE = '[rx:calc((100%_+_2.5px)*0.33_-_1.25px)] [stroke-dashoffset:-5.2]'

const player = useRadioPlayer()

const shape = computed(() => (player.playing.value ? SQUARE : CIRCLE))

const dash = computed(() => {
  const arc = player.progress.value * 100
  return arc < 100 ? `${arc} ${100 - arc}` : undefined
})
</script>
