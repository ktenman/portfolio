<template>
  <button :type="type" :class="classes" :disabled="disabled || loading">
    <span v-if="loading" class="btn-spinner me-1" role="status" aria-hidden="true"></span>
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

const classes = computed(() => [
  'btn',
  props.variant ? `btn-${props.variant}` : undefined,
  props.size ? `btn-${props.size}` : undefined,
  props.ghost ? 'btn-ghost' : undefined,
])
</script>
