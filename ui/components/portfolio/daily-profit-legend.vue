<template>
  <div class="calendar-legend">
    <div class="legend-scale">
      <span class="legend-text legend-loss">Loss</span>
      <span v-for="swatch in SWATCHES" :key="swatch" class="legend-swatch" :class="swatch" />
      <span
        v-for="tick in ticks"
        :key="tick.column"
        class="legend-text legend-tick"
        :style="{ gridColumn: tick.column }"
      >
        {{ tick.text }}
      </span>
      <span class="legend-text legend-gain">Gain</span>
    </div>
    <span v-if="missing" class="legend-missing">
      <daily-profit-cell variant="missing" class="legend-key" />
      <span class="legend-text">No daily summary</span>
    </span>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { CHANGE_STEPS } from '../../services/daily-profit-calendar'
import DailyProfitCell from './daily-profit-cell.vue'

const props = defineProps<{ missing: boolean; steps?: number[] }>()

const SWATCHES = [
  'rounded-l-control bg-loss',
  'bg-loss-step-3',
  'bg-loss-step-2',
  'bg-loss-step-1',
  'grid place-content-center after:size-2 after:rounded-control after:bg-change-flat',
  'bg-gain-step-1',
  'bg-gain-step-2',
  'bg-gain-step-3',
  'rounded-r-control bg-gain',
]
const FIRST_COLUMN = 2
const FLAT_COLUMN = 6

const ticks = computed(() => {
  const [flat, ...upper] = (props.steps ?? CHANGE_STEPS).map(String)
  return [
    ...[...upper].reverse().map((text, index) => ({
      text,
      column: `${FIRST_COLUMN + index} / span 2`,
    })),
    { text: `±${flat}%`, column: `${FLAT_COLUMN}` },
    ...upper.map((text, index) => ({ text, column: `${FLAT_COLUMN + 1 + index} / span 2` })),
  ]
})
</script>

<style scoped>
.calendar-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  align-items: flex-start;
  padding-top: 28px;
  font-size: var(--text-label);
  line-height: 1;
  color: var(--color-ink-soft);
}

.legend-scale {
  display: grid;
  grid-template-columns: auto repeat(9, max(2.5em, 3.75ch)) auto;
  align-items: center;
  row-gap: 3px;
}

.legend-loss {
  min-width: 27px;
  margin-right: 8px;
}

.legend-gain {
  margin-left: 8px;
}

.legend-swatch {
  height: 6px;
}

.legend-missing {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.legend-key {
  width: 12px;
}

.legend-tick {
  grid-row: 2;
  justify-self: center;
}
</style>
