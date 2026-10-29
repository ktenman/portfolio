<template>
  <div class="flex items-center gap-1.5" role="group" aria-labelledby="compare-label">
    <span id="compare-label" class="me-1 text-label font-medium text-ink">Compare</span>
    <button
      v-for="benchmark in BENCHMARKS"
      :key="benchmark.key"
      class="platform-btn md-down:min-h-11"
      :class="{ active: selected.includes(benchmark.key) }"
      :aria-pressed="selected.includes(benchmark.key)"
      type="button"
      @click="emit('select', benchmark.key)"
    >
      <span
        class="size-2 shrink-0 rounded-full"
        :style="{ backgroundColor: benchmark.color }"
        aria-hidden="true"
      ></span>
      {{ benchmark.label }}
    </button>
  </div>
</template>

<script lang="ts" setup>
import { BENCHMARKS, type BenchmarkKey } from '../../composables/use-portfolio-chart'

defineProps<{ selected: BenchmarkKey[] }>()

const emit = defineEmits<{ select: [key: BenchmarkKey] }>()
</script>
