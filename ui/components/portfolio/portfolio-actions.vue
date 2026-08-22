<template>
  <div class="mb-6 flex flex-wrap items-center gap-3">
    <h2 class="mb-0 hidden sm:block">Portfolio Summary</h2>
    <slot name="title-suffix" />
    <AppButton
      variant="secondary"
      size="sm"
      ghost
      class="ml-auto whitespace-nowrap max-sm:border-transparent max-sm:bg-transparent max-sm:pr-0 max-sm:shadow-none"
      :loading="isRecalculating"
      @click="$emit('recalculate')"
      :disabled="isLoading"
    >
      {{ isRecalculating ? 'Recalculating...' : 'Recalculate Data' }}
    </AppButton>
  </div>
</template>

<script setup lang="ts">
import AppButton from '../shared/app-button.vue'

interface Props {
  isRecalculating: boolean
  isLoading: boolean
}

defineProps<Props>()

defineEmits<{
  recalculate: []
}>()
</script>
