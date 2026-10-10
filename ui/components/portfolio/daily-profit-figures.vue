<template>
  <div class="day-figures">
    <dl class="figure-grid">
      <div class="figure">
        <dt class="figure-label">Up days</dt>
        <dd class="figure-value">{{ calendar.upDays }}</dd>
      </div>
      <div class="figure">
        <dt class="figure-label">Down days</dt>
        <dd class="figure-value">{{ calendar.downDays }}</dd>
      </div>
      <div class="figure">
        <dt class="figure-label">Flat days</dt>
        <dd class="figure-value">
          {{ calendar.flatDays }}
          <span class="figure-hint">{{ FLAT_HINT }}</span>
        </dd>
      </div>
      <div class="figure">
        <dt class="figure-label">Average day</dt>
        <dd class="figure-value" :class="getGainLossClass(calendar.averageDay)">
          {{ formatCurrencyChange(calendar.averageDay) }}
        </dd>
      </div>
    </dl>
    <div v-if="extremes.length" class="extreme-buttons">
      <app-button
        v-for="extreme in extremes"
        :key="extreme.label"
        class="extreme-btn"
        @click="emit('select', extreme.day.date)"
      >
        <span class="extreme-content">
          <daily-profit-cell :variant="extreme.variant" class="extreme-swatch" />
          <span>{{ extreme.label }}</span>
          <span class="extreme-amount" :class="getGainLossClass(extreme.day.change)">
            {{ formatCurrencyChange(extreme.day.change) }}
          </span>
          <span class="extreme-date">{{ formatDayMonth(extreme.day.date) }}</span>
        </span>
      </app-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  extremeDays,
  FLAT_HINT,
  type DailyProfitCalendar,
} from '../../services/daily-profit-calendar'
import { formatCurrencyChange, getGainLossClass } from '../../utils/formatters'
import { formatDayMonth } from '../../utils/iso-dates'
import AppButton from '../shared/app-button.vue'
import DailyProfitCell from './daily-profit-cell.vue'

const props = defineProps<{ calendar: DailyProfitCalendar }>()

const emit = defineEmits<{ select: [date: string] }>()

const extremes = computed(() => extremeDays(props.calendar))
</script>

<style scoped>
.day-figures {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.figure-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 0;
  border: 1px solid var(--color-hairline);
  border-radius: var(--radius-container);
}

.figure {
  padding: 12px 14px;
}

.figure:nth-child(even) {
  border-left: 1px solid var(--color-hairline);
}

.figure:nth-child(n + 3) {
  border-top: 1px solid var(--color-hairline);
}

.figure-label {
  margin-bottom: 0.25rem;
  font-size: var(--text-label);
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-ink-soft);
}

.figure-value {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 550;
  line-height: 1.2;
}

.figure-hint {
  font-size: var(--text-2xs);
  font-weight: 400;
  color: var(--color-ink-soft);
}

.extreme-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.extreme-content {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  font-size: var(--text-2xs);
}

.extreme-swatch {
  width: 10px;
}

.extreme-amount {
  font-weight: 550;
}

.extreme-date {
  font-weight: 400;
  color: var(--color-ink-soft);
}
</style>
