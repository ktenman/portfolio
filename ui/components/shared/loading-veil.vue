<template>
  <div class="loading-veil" :class="{ delayed }">
    <loading-spinner :message="message" />
  </div>
</template>

<script lang="ts" setup>
import LoadingSpinner from './loading-spinner.vue'

defineProps<{ message: string; delayed?: boolean }>()
</script>

<style scoped>
.loading-veil {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--color-surface) 72%, transparent);
}

.delayed {
  animation: veil-wait 200ms;
}

@keyframes veil-wait {
  from,
  to {
    opacity: 0;
  }
}
</style>
