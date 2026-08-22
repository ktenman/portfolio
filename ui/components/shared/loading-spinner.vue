<template>
  <div class="flex justify-center items-center" :class="containerClass">
    <div class="loading-spinner animate-btn-spin" :class="`spinner-${size}`" role="status">
      <span class="sr-only">{{ message }}</span>
    </div>
    <span v-if="showMessage" class="ml-2">{{ message }}</span>
  </div>
</template>

<script setup lang="ts">
interface LoadingSpinnerProps {
  message?: string
  showMessage?: boolean
  size?: 'sm' | 'md' | 'lg'
  containerClass?: string
}

withDefaults(defineProps<LoadingSpinnerProps>(), {
  message: 'Loading...',
  showMessage: false,
  size: 'md',
  containerClass: '',
})
</script>

<style scoped>
.loading-spinner {
  display: inline-block;
  border: 3px solid var(--color-hairline);
  border-top-color: var(--color-signal-indigo);
  border-radius: 50%;
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
