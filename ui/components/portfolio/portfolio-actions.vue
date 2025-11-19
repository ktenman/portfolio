<template>
  <div class="d-flex justify-content-between align-items-center mb-4">
    <h2 class="mb-0">Portfolio Summary</h2>
    <button
      class="btn btn-ghost btn-sm btn-secondary"
      @click="$emit('recalculate')"
      :disabled="isRecalculating || isLoading"
    >
      <span
        v-if="isRecalculating"
        class="spinner-border spinner-border-sm me-1"
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
