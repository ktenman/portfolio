<template>
  <div class="calendar-legend">
    <span class="legend-group">
      <span class="legend-text">Loss</span>
      <daily-profit-cell
        v-for="step in LEGEND_STEPS"
        :key="step"
        :variant="step"
        class="legend-swatch"
      />
      <span class="legend-text">Gain</span>
    </span>
    <span class="legend-text">{{ STEPS_TEXT }}</span>
    <span class="legend-group">
      <daily-profit-cell variant="missing" class="legend-swatch" />
      <span class="legend-text">No daily summary</span>
    </span>
  </div>
</template>

<script lang="ts" setup>
import { CHANGE_STEPS, type ChangeStep } from '../../services/daily-profit-calendar'
import DailyProfitCell from './daily-profit-cell.vue'

const LEGEND_STEPS: ChangeStep[] = [
  'loss-4',
  'loss-3',
  'loss-2',
  'loss-1',
  'flat',
  'gain-1',
  'gain-2',
  'gain-3',
  'gain-4',
]
const STEPS_TEXT = `Steps at ${CHANGE_STEPS.map(step => `${step}%`).join(' · ')}`
</script>

<style scoped>
.calendar-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 20px;
  margin-top: 19px;
  font-size: var(--text-label);
  color: var(--color-ink-soft);
}

.legend-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-swatch {
  width: 14px;
}
</style>
