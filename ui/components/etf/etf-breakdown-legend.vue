<template>
  <div class="chart-legend" role="region" aria-label="Breakdown legend" @mouseleave="emit('leave')">
    <div
      v-for="(item, index) in items"
      :key="item.label"
      class="legend-item"
      :class="{
        active: index === activeIndex,
        dimmed: activeIndex !== null && index !== activeIndex,
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
    </div>
  </div>
</template>

<script lang="ts" setup>
import type { ChartDataItem } from '../../services/etf-chart-service'

defineProps<{
  items: ChartDataItem[]
  activeIndex: number | null
}>()

const emit = defineEmits<{
  hover: [index: number]
  leave: []
}>()
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
