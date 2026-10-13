<template>
  <div class="monthly-profit-legend">
    <div class="monthly-profit-legend-scale">
      <span class="monthly-profit-legend-loss">Loss</span>
      <span
        v-for="step in STEPS"
        :key="step"
        class="monthly-profit-legend-swatch monthly-profit-swatch"
        :class="step"
        aria-hidden="true"
      />
      <span class="monthly-profit-legend-gain">Gain</span>
      <span
        v-for="tick in ticks"
        :key="tick.column"
        class="monthly-profit-legend-tick"
        :style="{ gridColumn: tick.column }"
      >
        {{ tick.text }}
      </span>
    </div>
    <span v-if="missing" class="monthly-profit-legend-missing">
      <span class="monthly-profit-swatch missing" aria-hidden="true" />
      <span>No daily summary</span>
    </span>
  </div>
</template>

<script lang="ts" setup>
import { MONTHLY_CHANGE_STEPS } from '../../services/monthly-profit'

defineProps<{ missing: boolean }>()

const STEPS = [
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
const [flat, ...upper] = MONTHLY_CHANGE_STEPS.map(String)
const ticks = [
  ...[...upper].reverse().map((text, index) => ({ text, column: `${2 + index} / span 2` })),
  { text: `±${flat}%`, column: '6' },
  ...upper.map((text, index) => ({ text, column: `${7 + index} / span 2` })),
]
</script>
