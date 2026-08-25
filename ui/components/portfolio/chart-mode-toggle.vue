<template>
  <div class="platform-buttons">
    <button
      v-for="mode in modes"
      :key="mode.value"
      class="platform-btn"
      :class="{ active: isActive(mode.value) }"
      type="button"
      @click="emit('select', mode.value)"
    >
      {{ mode.label }}
    </button>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  CHART_MODES,
  type BenchmarkKey,
  type ChartMode,
} from '../../composables/use-portfolio-chart'

const props = defineProps<{ selected: BenchmarkKey[]; worldAvailable: boolean }>()

const emit = defineEmits<{ select: [mode: ChartMode] }>()

const modes = computed(() =>
  CHART_MODES.filter(mode => mode.value !== 'world' || props.worldAvailable)
)

const isActive = (value: ChartMode) =>
  value === 'value' ? props.selected.length === 0 : props.selected.includes(value)
</script>
