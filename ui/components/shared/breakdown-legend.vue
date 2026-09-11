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
      @click="toggle(index)"
    >
      <img v-if="item.code" :src="countryFlagUrl(item.code)" :alt="item.code" class="legend-flag" />
      <span v-else class="legend-color" :style="{ backgroundColor: item.color }"></span>
      <span class="legend-label">{{ item.label }}</span>
      <span class="legend-value">{{ formatPercentage(item.value) }}</span>
      <span
        v-if="item.benchmark !== undefined"
        class="legend-benchmark"
        :class="{ flagged: isFlagged(item.ratio) }"
      >
        {{ formatBenchmarkShare(item.benchmark, item.ratio) }}
      </span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {
  formatBenchmarkShare,
  isFlagged,
  type BreakdownRow,
} from '../../services/diversification-chart-service'
import { countryFlagUrl } from '../../utils/currency-flag'
import { formatPercentage } from '../../utils/formatters'

const props = defineProps<{
  items: BreakdownRow[]
  activeIndex: number | null
}>()

const emit = defineEmits<{
  hover: [index: number]
  leave: []
}>()

const toggle = (index: number) =>
  index === props.activeIndex ? emit('leave') : emit('hover', index)
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

@media (max-width: 639px) {
  .chart-legend {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.75rem 0.75rem;
  }

  .legend-item {
    align-items: start;
    align-content: start;
  }

  .legend-color,
  .legend-flag {
    margin-top: 0.3rem;
  }

  .legend-label {
    font-size: var(--text-label);
    font-weight: 600;
    letter-spacing: 0.05em;
    text-transform: uppercase;
    line-height: 1.3;
    white-space: normal;
  }

  .legend-value {
    font-size: var(--text-sm);
    font-weight: 400;
  }
}
</style>
