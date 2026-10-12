<template>
  <div class="day-panel">
    <p class="day-label">{{ formatFullDate(day.date) }}</p>
    <p class="day-change" :class="getGainLossClass(day.change)">
      <template v-if="day.kind === 'counted'">
        <span class="day-amount">
          <span>{{ amount[0] }}</span>
          <span class="day-cents">{{ amount[1] }}</span>
        </span>
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
      <daily-profit-arrow
        v-for="button in buttons"
        :key="button.label"
        :label="button.label"
        :direction="button.direction"
        :disabled="!button.target"
        @press="go(button.target)"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { CalendarDay } from '../../services/daily-profit-calendar'
import {
  formatCurrencyChange,
  formatPercentChange,
  getGainLossClass,
  splitCents,
} from '../../utils/formatters'
import { formatFullDate } from '../../utils/iso-dates'
import DailyProfitArrow from './daily-profit-arrow.vue'

const props = defineProps<{
  day: CalendarDay
  previous: string | null
  next: string | null
}>()

const emit = defineEmits<{ select: [date: string] }>()

const amount = computed(() => splitCents(formatCurrencyChange(props.day.change ?? 0)))

const buttons = computed(() => [
  { label: 'Previous day', target: props.previous, direction: 'back' as const },
  { label: 'Next day', target: props.next, direction: 'forward' as const },
])

const go = (target: string | null) => {
  if (target) emit('select', target)
}
</script>

<style scoped>
.day-panel {
  position: relative;
  container-type: inline-size;
}

.day-label {
  margin: 0;
  font-size: var(--text-label);
  font-weight: 500;
  line-height: 1.35rem;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-ink-soft);
}

.day-change {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-height: 1lh;
  margin: 6px 0 0;
  font-size: var(--text-title);
  font-weight: 550;
  line-height: 1.2;
}

.day-cents {
  font-size: 0.5em;
  font-weight: 500;
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
  top: calc((1.35rem - 2rem) / 2);
  right: -8px;
  display: flex;
}

@media (max-width: 768px) {
  .day-buttons {
    top: calc((1.35rem - 2.75rem) / 2);
    right: -14px;
  }
}

@container (min-width: 580px) {
  .day-texts {
    display: flex;
    gap: 12px;
    min-height: 1lh;
  }

  .day-note {
    margin-top: 0;
  }
}
</style>
