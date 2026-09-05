<template>
  <div class="chart-legend" role="region" aria-label="Breakdown legend" @mouseleave="emit('leave')">
    <div
      v-for="(item, index) in items"
      :key="item.label"
      class="legend-item"
      :class="{
        active: index === activeIndex,
        dimmed: activeIndex !== null && index !== activeIndex,
        compared: item.benchmark !== undefined,
      }"
      @mouseenter="emit('hover', index)"
    >
      <img
        v-if="item.code"
        :src="`https://hatscripts.github.io/circle-flags/flags/${item.code.toLowerCase()}.svg`"
        :alt="item.code"
        class="legend-flag"
      />
      <span v-else class="legend-color" :style="{ backgroundColor: item.color }"></span>
      <span class="legend-label">{{ item.label }}</span>
      <span class="legend-value">{{ item.percentage }}%</span>
      <span
        v-if="item.benchmark !== undefined"
        class="legend-benchmark"
        :class="{ flagged: isFlagged(item.ratio) }"
      >
        {{ formatBenchmark(item) }}
      </span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import type { ChartDataItem } from '../../services/etf-chart-service'

const props = defineProps<{
  items: ChartDataItem[]
  activeIndex: number | null
  benchmarkLabel?: string
}>()

const emit = defineEmits<{
  hover: [index: number]
  leave: []
}>()

const formatBenchmark = (item: ChartDataItem): string => {
  const share = `${props.benchmarkLabel ?? 'vs'} ${(item.benchmark ?? 0).toFixed(2)}%`
  return item.ratio === undefined ? share : `${share} · ${item.ratio.toFixed(2)}x`
}

const isFlagged = (ratio: number | undefined): boolean =>
  ratio !== undefined && (ratio > 2 || ratio < 0.5)
</script>

<style scoped>
.chart-legend {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
  gap: 0.875rem 1.25rem;
  align-content: start;
}

.legend-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  column-gap: 0.625rem;
  row-gap: 0.0625rem;
  align-items: center;
  transition: opacity 0.12s ease;
}

.legend-item.dimmed {
  opacity: 0.35;
}

.legend-color,
.legend-flag {
  grid-row: 1 / span 2;
}

.legend-item.compared .legend-color,
.legend-item.compared .legend-flag {
  grid-row: 1 / span 3;
}

.legend-benchmark {
  grid-column: 2;
  font-size: var(--text-label);
  color: var(--color-ink-muted);
  white-space: nowrap;
}

.legend-benchmark.flagged {
  color: var(--color-brass-deep);
  font-weight: 600;
}

.legend-color {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1px solid var(--color-hairline-strong);
}

.legend-flag {
  width: 16px;
  height: 16px;
  border-radius: 50%;
}

.legend-label {
  grid-column: 2;
  font-size: var(--text-base);
  color: var(--color-ink-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.legend-value {
  grid-column: 2;
  font-size: 1.0625rem;
  font-weight: 500;
  color: var(--color-ink);
}

.legend-item.active .legend-label {
  color: var(--color-ink);
}
</style>
