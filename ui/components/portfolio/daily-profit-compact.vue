<template>
  <div>
    <div class="compact-head">
      <div class="compact-title">
        <h2 class="compact-heading">Daily profit</h2>
        <span class="compact-counts">{{ calendar.upDays }} up · {{ calendar.downDays }} down</span>
      </div>
      <div class="compact-period"><slot /></div>
      <daily-profit-grid
        :calendar="calendar"
        compact
        :selected="day.date"
        :busy="busy"
        @select="emit('select', $event)"
      />
    </div>
    <div class="compact-day">
      <daily-profit-panel
        :day="day"
        :previous="moveDay(calendar, day.date, -1)"
        :next="moveDay(calendar, day.date, 1)"
        @select="emit('select', $event)"
      />
    </div>
    <div class="compact-row">
      <div class="compact-flat">
        <p class="compact-label">Flat days</p>
        <p class="compact-sub">{{ FLAT_HINT }}</p>
      </div>
      <span class="compact-value">{{ calendar.flatDays }}</span>
    </div>
    <div class="compact-row">
      <p class="compact-label">Average day</p>
      <span class="compact-value" :class="getGainLossClass(calendar.averageDay)">
        <span>{{ average[0] }}</span>
        <span class="compact-cents">{{ average[1] }}</span>
      </span>
    </div>
    <button
      v-for="extreme in extremes"
      :key="extreme.label"
      type="button"
      class="compact-row compact-pick"
      @click="emit('select', extreme.day.date)"
    >
      <span class="compact-pick-label">
        <daily-profit-cell :variant="extreme.variant" class="compact-swatch" />
        <span class="compact-label">
          {{ extreme.label }} · {{ formatWeekdayDate(extreme.day.date) }}
        </span>
      </span>
      <span class="compact-value" :class="getGainLossClass(extreme.day.change)">
        <span>{{ extreme.amount[0] }}</span>
        <span class="compact-cents">{{ extreme.amount[1] }}</span>
      </span>
    </button>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  extremeDays,
  FLAT_HINT,
  moveDay,
  type CalendarDay,
  type DailyProfitCalendar,
} from '../../services/daily-profit-calendar'
import { formatCurrencyChange, getGainLossClass, splitCents } from '../../utils/formatters'
import { formatWeekdayDate } from '../../utils/iso-dates'
import DailyProfitCell from './daily-profit-cell.vue'
import DailyProfitGrid from './daily-profit-grid.vue'
import DailyProfitPanel from './daily-profit-panel.vue'

const props = defineProps<{ calendar: DailyProfitCalendar; day: CalendarDay; busy?: boolean }>()

const emit = defineEmits<{ select: [date: string] }>()

const average = computed(() => splitCents(formatCurrencyChange(props.calendar.averageDay)))

const extremes = computed(() =>
  extremeDays(props.calendar).map(extreme => ({
    ...extreme,
    amount: splitCents(formatCurrencyChange(extreme.day.change)),
  }))
)
</script>

<style scoped src="./profit-compact.css"></style>

<style scoped>
.compact-head {
  padding: 14px 14px 10px;
}

.compact-period {
  margin-inline: -14px;
}

.compact-day {
  padding: 12px 14px;
  border-top: 1px solid var(--color-hairline);
}

.compact-pick {
  width: 100%;
  min-height: 44px;
  text-align: start;
  cursor: pointer;
  transition: background-color var(--transition-chip);
}

.compact-pick:last-child {
  border-radius: 0 0 var(--radius-container) var(--radius-container);
}

.compact-pick:focus-visible {
  outline-offset: -2px;
}

@media (hover: hover) {
  .compact-pick:hover {
    background: var(--color-surface-hover);
  }
}

.compact-pick:active {
  background: var(--color-surface-sunken);
}

.compact-pick-label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.compact-swatch {
  flex: none;
  width: 9px;
}
</style>
