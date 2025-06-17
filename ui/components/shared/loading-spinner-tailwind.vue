<template>
  <div class="flex justify-center items-center" :class="containerClass">
    <div
      class="animate-spin rounded-full border-b-2"
      :class="spinnerClasses"
      role="status"
      :aria-label="message"
    >
      <span class="sr-only">{{ message }}</span>
    </div>
    <span v-if="showMessage" class="ml-2">{{ message }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface LoadingSpinnerProps {
  message?: string
  showMessage?: boolean
  size?: 'sm' | 'md' | 'lg'
  containerClass?: string
}

const props = withDefaults(defineProps<LoadingSpinnerProps>(), {
  message: 'Loading...',
  showMessage: false,
  size: 'md',
  containerClass: '',
})

const spinnerClasses = computed(() => {
  const sizeClasses = {
    sm: 'h-4 w-4 border-blue-600',
    md: 'h-8 w-8 border-blue-600',
    lg: 'h-12 w-12 border-blue-600',
  }
  return sizeClasses[props.size]
})
</script>