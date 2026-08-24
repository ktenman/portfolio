<template>
  <div class="platform-buttons">
    <button
      v-for="mode in MODES"
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
const MODES: { value: ChartMode; label: string }[] = [
  { value: 'value', label: '€' },
  { value: 'performance', label: '% vs S&P 500' },
]

defineProps<{ selected: ChartMode }>()

const emit = defineEmits<{ select: [mode: ChartMode] }>()
</script>
