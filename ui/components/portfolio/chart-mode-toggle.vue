<template>
  <div class="platform-buttons">
    <button
      class="platform-btn"
      :class="{ active: selected.length === 0 }"
      type="button"
      @click="emit('select', null)"
    >
      €
    </button>
    <button
      v-for="benchmark in BENCHMARKS"
      :key="benchmark.key"
      class="platform-btn"
      :class="{ active: selected.includes(benchmark.key) }"
      type="button"
      @click="emit('select', benchmark.key)"
    >
      % vs {{ benchmark.label }}
    </button>
  </div>
</template>

<script lang="ts" setup>
import { BENCHMARKS, type BenchmarkKey } from '../../composables/use-portfolio-chart'

defineProps<{ selected: BenchmarkKey[] }>()

const emit = defineEmits<{ select: [key: BenchmarkKey | null] }>()
</script>
