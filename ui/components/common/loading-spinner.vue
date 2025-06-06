<template>
  <div class="d-flex justify-content-center align-items-center" :class="containerClass">
    <div class="spinner-border text-primary" :class="spinnerClass" role="status">
      <span class="visually-hidden">{{ message }}</span>
    </div>
    <span v-if="showMessage" class="ms-2">{{ message }}</span>
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

const spinnerClass = computed(() => {
  const sizeMap = {
    sm: 'spinner-border-sm',
    md: '',
    lg: '',
  }
  return sizeMap[props.size]
})
</script>
