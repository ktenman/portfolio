<template>
  <div :class="classes" role="alert">
    <slot />
    <button
      v-if="dismissible"
      type="button"
      class="btn-close"
      aria-label="Close"
      @click="emit('dismiss')"
    ></button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  variant: 'danger' | 'info' | 'warning'
  dismissible?: boolean
}>()

const emit = defineEmits<{ dismiss: [] }>()

const classes = computed(() => [
  'alert',
  `alert-${props.variant}`,
  props.dismissible ? 'alert-dismissible' : undefined,
])
</script>
