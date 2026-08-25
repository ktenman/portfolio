<template>
  <div class="platform-buttons">
    <button
      v-for="mode in modes"
      :key="mode.value"
      class="platform-btn"
      :class="{ active: mode.value === selected }"
      type="button"
      @click="emit('select', mode.value)"
    >
      {{ mode.label }}
    </button>
  </div>
</template>

<script lang="ts">
export type ChartMode = 'value' | 'performance'
</script>

<script lang="ts" setup>
defineProps<{ selected: ChartMode }>()

const emit = defineEmits<{ select: [mode: ChartMode] }>()

const modes: { value: ChartMode; label: string }[] = [
  { value: 'value', label: '€' },
  { value: 'performance', label: '%' },
]
</script>
