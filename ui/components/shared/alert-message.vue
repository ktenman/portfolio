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

const BASE = 'alert relative mb-4 rounded-control border p-4'

const VARIANTS = {
  danger: 'alert-danger border-loss-wash-deep bg-loss-wash text-loss-deep',
  info: 'alert-info border-hairline bg-surface-sunken text-ink',
  warning: 'alert-warning border-brass bg-brass-wash text-brass-deep',
}

const classes = computed(() => [
  BASE,
  VARIANTS[props.variant],
  props.dismissible ? 'alert-dismissible pr-12' : undefined,
])
</script>
