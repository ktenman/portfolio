<template>
  <div class="platform-buttons">
    <button
      v-for="range in TIME_RANGES"
      :key="range"
      class="platform-btn"
      :class="{ active: range === selected }"
      type="button"
      @click="emit('select', range)"
    >
      {{ range }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { TIME_RANGES } from '../../composables/use-time-range'
import type { TimeRange } from '../../models/generated/domain-models'

defineProps<{
  selected: TimeRange
}>()

const emit = defineEmits<{
  select: [range: TimeRange]
}>()
</script>
