<template>
  <div class="tw:mb-6 tw:flex tw:items-center tw:justify-between">
    <h2 class="tw:mb-0">Portfolio Summary</h2>
    <button
      class="btn btn-ghost btn-sm btn-secondary"
      @click="$emit('recalculate')"
      :disabled="isRecalculating || isLoading"
    >
      <span
        v-if="isRecalculating"
        class="btn-spinner tw:mr-1"
        role="status"
        aria-hidden="true"
      ></span>
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
