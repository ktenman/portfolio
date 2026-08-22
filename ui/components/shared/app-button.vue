<template>
  <button :type="type" :class="classes" :disabled="disabled || loading">
    <span
      v-if="loading"
      class="btn-spinner me-1 inline-block size-3.5 animate-btn-spin rounded-full border-2 border-r-transparent align-[-0.125em]"
      role="status"
      aria-hidden="true"
    ></span>
    <slot />
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'danger'
    size?: 'sm'
    ghost?: boolean
    loading?: boolean
    disabled?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: undefined, size: undefined, type: 'button' }
)

const BASE =
  'btn inline-block cursor-pointer rounded-container border border-control-border text-center align-middle leading-normal font-medium shadow-control transition-all select-none disabled:cursor-not-allowed disabled:opacity-60 enabled:active:shadow-[inset_0_1px_1px_rgb(0_0_0/0.04)] md-down:min-h-11 [&_svg]:mr-1.5 [&_svg]:align-[-0.125em] [&_svg:last-child]:mr-0 [&_svg:last-child]:ml-1.5'

const SOLID = {
  none: 'bg-transparent text-ink enabled:active:translate-y-px',
  primary: 'bg-signal-indigo text-white enabled:hover:bg-signal-indigo-deep',
  secondary: 'bg-gray-600 text-white enabled:hover:bg-gray-700 enabled:active:translate-y-px',
  danger: 'bg-loss text-white enabled:hover:bg-loss-deep',
}

const GHOST = {
  none: 'text-ink enabled:active:translate-y-px',
  primary: 'text-white enabled:hover:bg-signal-indigo-deep',
  secondary:
    'text-gray-600 enabled:hover:bg-brass-wash enabled:hover:text-brass-deep enabled:active:translate-y-px',
  danger: 'text-ink enabled:active:translate-y-px',
}

const sizing = computed(() => {
  if (props.size !== 'sm') return 'px-3 py-1.5 text-control'
  if (!props.ghost) return 'px-3.5 py-1.5 text-sm'
  return 'px-2.5 py-1 text-sm md-compact:inline-flex md-compact:items-center md-compact:justify-center md-compact:px-2 md-compact:py-1.5'
})

const classes = computed(() => [
  BASE,
  sizing.value,
  props.variant ? `btn-${props.variant}` : undefined,
  props.size ? `btn-${props.size}` : undefined,
  props.ghost ? 'btn-ghost bg-[rgb(0_0_0/0.02)] [.btn-ghost+&]:ml-1.5' : undefined,
  props.ghost ? GHOST[props.variant ?? 'none'] : SOLID[props.variant ?? 'none'],
])
</script>
