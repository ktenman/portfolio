<template>
  <div :class="classes" role="alert">
    <slot />
    <close-button
      v-if="dismissible"
      class="absolute top-0 right-0 z-2 px-4 py-5"
      @click="emit('dismiss')"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import CloseButton from './close-button.vue'

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
