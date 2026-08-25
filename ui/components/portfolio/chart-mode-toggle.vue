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
export type ChartMode = 'value' | 'sp500' | 'world'
</script>

<script lang="ts" setup>
import { computed } from 'vue'

const props = defineProps<{ selected: ChartMode; worldAvailable: boolean }>()

const emit = defineEmits<{ select: [mode: ChartMode] }>()

const modes = computed<{ value: ChartMode; label: string }[]>(() => [
  { value: 'value', label: '€' },
  { value: 'sp500', label: '% vs S&P 500' },
  ...(props.worldAvailable ? [{ value: 'world' as const, label: '% vs World' }] : []),
])
</script>
