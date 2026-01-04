<template>
  <div class="d-flex justify-content-center align-items-center" :class="containerClass">
    <div class="loading-spinner" :class="[spinnerClass, sizeClass]" role="status">
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

const sizeClass = computed(() => {
  const sizeMap = {
    sm: 'spinner-sm',
    md: 'spinner-md',
    lg: 'spinner-lg',
  }
  return sizeMap[props.size]
})
</script>

<style scoped>
@keyframes spinner-rotate {
  to {
    transform: rotate(360deg);
  }
}

.loading-spinner {
  display: inline-block;
  border: 3px solid #e9ecef;
  border-top-color: #0d6efd;
  border-radius: 50%;
  animation: spinner-rotate 0.75s linear infinite;
}

.spinner-sm {
  width: 1rem;
  height: 1rem;
  border-width: 2px;
}

.spinner-md {
  width: 2rem;
  height: 2rem;
  border-width: 3px;
}

.spinner-lg {
  width: 3rem;
  height: 3rem;
  border-width: 4px;
}
</style>
