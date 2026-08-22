<template>
  <span :class="classes" role="status">
    <span v-if="label" class="sr-only">{{ label }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ size?: 'sm' | 'md'; label?: string }>(), { size: 'md' })

const classes = computed(() => [
  'spinner-border inline-block shrink-0 animate-btn-spin rounded-full border-r-transparent align-[-0.125em]',
  props.size === 'sm' ? 'spinner-border-sm size-4 border-[0.2em]' : 'size-8 border-[0.25em]',
])
</script>
