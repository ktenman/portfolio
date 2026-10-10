<template>
  <div>
    <div class="compact-head">
      <div class="compact-title">
        <h2 class="compact-heading">Every day since {{ formatDayMonth(calendar.days[0].date) }}</h2>
        <span class="compact-counts">{{ calendar.upDays }} up · {{ calendar.downDays }} down</span>
      </div>
      <daily-profit-grid :calendar="calendar" compact />
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
        {{ formatCurrencyChange(calendar.averageDay) }}
      </span>
    </div>
    <div v-for="extreme in extremes" :key="extreme.label" class="compact-row compact-extreme">
      <div>
        <p class="compact-label">{{ extreme.label }} · {{ formatWeekdayDate(extreme.day.date) }}</p>
        <p class="compact-sub">{{ extreme.day.closing }}</p>
      </div>
      <div class="compact-change" :class="getGainLossClass(extreme.day.change)">
        <span class="compact-amount">{{ formatCurrencyChange(extreme.day.change) }}</span>
        <span v-if="extreme.day.percent !== null" class="compact-percent">
          {{ formatPercentChange(extreme.day.percent) }}
        </span>
      </div>
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
import { formatCurrencyChange, formatPercentChange, getGainLossClass } from '../../utils/formatters'
import { formatDayMonth, formatWeekdayDate } from '../../utils/iso-dates'
import DailyProfitGrid from './daily-profit-grid.vue'

const props = defineProps<{ calendar: DailyProfitCalendar }>()

const extremes = computed(() => extremeDays(props.calendar))
</script>

<style scoped>
.compact-head {
  padding: 14px 14px 10px;
}

.compact-title {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  column-gap: 8px;
  margin-bottom: 12px;
}

.compact-heading {
  margin: 0;
  font-size: 1rem;
}

.compact-counts {
  font-size: var(--text-label);
  color: var(--color-ink-soft);
}

.compact-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--color-hairline);
}

.compact-extreme {
  padding-block: 12px;
}

.compact-flat {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.compact-label,
.compact-sub {
  margin: 0;
}

.compact-label {
  font-size: var(--text-label);
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-ink-soft);
}

.compact-sub {
  font-size: var(--text-2xs);
  color: var(--color-ink-soft);
}

.compact-value,
.compact-change {
  white-space: nowrap;
}

.compact-value {
  font-size: var(--text-base);
  font-weight: 550;
}

.compact-change {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.compact-amount {
  font-size: var(--text-heading);
  font-weight: 550;
  line-height: 1.2;
}

.compact-percent {
  font-size: var(--text-label);
}
</style>
