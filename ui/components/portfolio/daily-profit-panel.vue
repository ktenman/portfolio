<template>
  <div class="day-panel">
    <p class="day-label">{{ formatFullDate(day.date) }}</p>
    <p class="day-change" :class="getGainLossClass(day.change)">
      <template v-if="day.kind === 'counted'">
        <span class="day-amount">{{ formatCurrencyChange(day.change) }}</span>
        <span v-if="day.percent !== null" class="day-percent">
          {{ formatPercentChange(day.percent) }}
        </span>
      </template>
    </p>
    <div class="day-texts">
      <p class="day-detail">{{ day.detail }}</p>
      <p v-if="day.note" class="day-note">{{ day.note }}</p>
    </div>
    <div class="day-buttons">
      <button
        v-for="button in buttons"
        :key="button.label"
        type="button"
        class="day-btn size-8 md-down:size-11"
        :aria-label="button.label"
        :title="button.label"
        :aria-disabled="button.target ? undefined : 'true'"
        @click="go(button.target)"
      >
        <svg viewBox="0 0 16 16" aria-hidden="true">
          <path :d="button.path" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { CalendarDay } from '../../services/daily-profit-calendar'
import { formatCurrencyChange, formatPercentChange, getGainLossClass } from '../../utils/formatters'
import { formatFullDate } from '../../utils/iso-dates'

const props = defineProps<{
  day: CalendarDay
  previous: string | null
  next: string | null
}>()

const emit = defineEmits<{ select: [date: string] }>()

const buttons = computed(() => [
  { label: 'Previous day', target: props.previous, path: 'M10 3.5 5.5 8l4.5 4.5' },
  { label: 'Next day', target: props.next, path: 'M6 3.5 10.5 8 6 12.5' },
])

const go = (target: string | null) => {
  if (target) emit('select', target)
}
</script>

<style scoped>
.day-panel {
  position: relative;
  padding: 18px 20px;
  background: var(--color-surface-sunken);
  border-radius: var(--radius-container);
}

.day-label {
  margin: 0;
  font-size: var(--text-label);
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-ink-soft);
}

.day-change {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-height: 1lh;
  margin: 8px 0 0;
  font-size: var(--text-title);
  font-weight: 550;
  line-height: 1.2;
}

.day-percent {
  font-size: var(--text-control);
}

.day-texts {
  min-height: calc(2lh + 2px);
  margin-top: 6px;
  font-size: var(--text-2xs);
  color: var(--color-ink-soft);
}

.day-detail,
.day-note {
  margin: 0;
}

.day-note {
  margin-top: 2px;
  color: var(--color-notice);
}

.day-buttons {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  border: 1px solid var(--color-control-border);
  border-radius: var(--radius-control);
}

.day-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  background: var(--color-surface);
  color: var(--color-ink);
  cursor: pointer;
}

.day-btn:first-child {
  border-radius: var(--radius-control) 0 0 var(--radius-control);
}

.day-btn + .day-btn {
  border-left: 1px solid var(--color-control-border);
  border-radius: 0 var(--radius-control) var(--radius-control) 0;
}

.day-btn:hover {
  background: var(--color-surface-hover);
}

.day-btn[aria-disabled='true'] {
  background: var(--color-surface);
  color: var(--color-ink-faint);
  cursor: default;
}

.day-btn svg {
  width: 1rem;
  height: 1rem;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
