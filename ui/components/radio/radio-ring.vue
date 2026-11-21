<template>
  <span
    class="pointer-events-none absolute inset-[1.25px]"
    aria-hidden="true"
    data-testid="radio-ring"
  >
    <svg class="block size-full -rotate-90 overflow-visible" fill="none" stroke-width="2.5">
      <circle cx="50%" cy="50%" r="50%" class="stroke-hairline" />
      <circle
        cx="50%"
        cy="50%"
        r="50%"
        class="stroke-brass"
        pathLength="100"
        :stroke-dasharray="dash"
      />
    </svg>
  </span>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useRadioPlayer } from '../../composables/use-radio-player'

const props = defineProps<{ share?: number }>()

const player = useRadioPlayer()

const dash = computed(() => {
  const share = props.share ?? player.progress.value
  return share < 1 ? `${share * 100} 100` : undefined
})
</script>
