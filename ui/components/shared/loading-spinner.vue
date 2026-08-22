<template>
  <div class="flex justify-center items-center">
    <div
      class="loading-spinner inline-block size-8 animate-btn-spin rounded-full border-[3px] border-hairline border-t-signal-indigo"
      role="status"
    >
      <span class="sr-only">{{ message }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ message?: string }>(), { message: 'Loading...' })
</script>
