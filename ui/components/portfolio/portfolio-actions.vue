<template>
  <div class="d-flex justify-content-between align-items-center mb-3">
    <h4>Portfolio Summary</h4>
    <button
      class="btn btn-secondary btn-sm"
      @click="$emit('recalculate')"
      :disabled="isRecalculating || isLoading"
    >
      <span
        v-if="isRecalculating"
        class="spinner-border spinner-border-sm me-1"
        role="status"
        aria-hidden="true"
      ></span>
      {{ isRecalculating ? 'Recalculating...' : 'Recalculate All Data' }}
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
