<template>
  <div>
    <div class="compact-head">
      <div class="compact-title">
        <h2 class="compact-heading">Daily profit</h2>
        <span class="compact-counts">{{ calendar.upDays }} up · {{ calendar.downDays }} down</span>
      </div>
      <div class="compact-period"><slot /></div>
      <daily-profit-grid :calendar="calendar" compact :busy="busy" />
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
    <div v-for="extreme in extremes" :key="extreme.label" class="compact-row compact-extreme">
      <div>
        <p class="compact-label">{{ extreme.label }} · {{ formatWeekdayDate(extreme.day.date) }}</p>
        <p class="compact-sub">{{ extreme.day.closing }}</p>
      </div>
      <div class="compact-change" :class="getGainLossClass(extreme.day.change)">
        <span class="compact-amount">
          <span>{{ extreme.amount[0] }}</span>
          <span class="compact-cents">{{ extreme.amount[1] }}</span>
        </span>
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
import {
  formatCurrencyChange,
  formatPercentChange,
  getGainLossClass,
  splitCents,
} from '../../utils/formatters'
import { formatWeekdayDate } from '../../utils/iso-dates'
import DailyProfitGrid from './daily-profit-grid.vue'

const props = defineProps<{ calendar: DailyProfitCalendar; busy?: boolean }>()

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
</style>
