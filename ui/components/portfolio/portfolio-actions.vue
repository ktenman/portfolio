<template>
  <div class="mb-6 flex flex-wrap items-center gap-x-3 gap-y-3">
    <h2 class="mb-0 hidden sm:block">Portfolio Summary</h2>
    <slot name="title-suffix" />
    <button
      class="btn btn-ghost btn-sm btn-secondary ml-auto whitespace-nowrap max-sm:border-transparent max-sm:bg-transparent max-sm:pr-0 max-sm:shadow-none"
      @click="$emit('recalculate')"
      :disabled="isRecalculating || isLoading"
    >
      <span v-if="isRecalculating" class="btn-spinner mr-1" role="status" aria-hidden="true"></span>
      {{ isRecalculating ? 'Recalculating...' : 'Recalculate Data' }}
    </button>
  </div>
</template>

<script setup lang="ts">
interface Props {
  isRecalculating: boolean
  isLoading: boolean
}

defineProps<Props>()

defineEmits<{
  recalculate: []
}>()
</script>
