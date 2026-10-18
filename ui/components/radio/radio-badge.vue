<template>
  <span
    class="inline-block rounded-control px-2 py-0.5 text-label font-semibold tracking-wide whitespace-nowrap uppercase"
    :class="TONES[tone]"
  >
    <slot />
  </span>
</template>

<script lang="ts" setup>
export type BadgeTone = 'plain' | 'live' | 'warn' | 'error'

withDefaults(defineProps<{ tone?: BadgeTone }>(), { tone: 'plain' })

const TONES: Record<BadgeTone, string> = {
  plain: 'bg-surface-sunken text-ink-soft',
  live: 'bg-notice-wash text-notice',
  warn: 'bg-brass-wash text-brass-deep',
  error: 'bg-loss-wash text-loss-deep',
}
</script>
