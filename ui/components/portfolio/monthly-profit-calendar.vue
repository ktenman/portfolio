<template>
  <div v-if="monthly && month" class="monthly-profit-calendar card-shell p-0 md:p-6">
    <div class="calendar-body hidden md:flex">
      <div class="calendar-main">
        <div class="calendar-head">
          <h2 class="calendar-heading">Monthly profit</h2>
          <span class="calendar-span">{{ monthly.span }}</span>
        </div>
        <monthly-profit-grid
          :monthly="monthly"
          :selected="month.key"
          :previewed="previewKey"
          @select="select"
          @preview="previewKey = $event"
        />
        <daily-profit-legend :steps="MONTH_STEPS" :missing="hasMissing" />
      </div>
      <div class="calendar-side">
        <monthly-profit-panel
          :month="preview ?? month"
          :previous="moveMonth(monthly, month.key, -1)"
          :next="moveMonth(monthly, month.key, 1)"
          @select="select"
        />
        <monthly-profit-figures class="calendar-period" :monthly="monthly" @select="select" />
      </div>
    </div>
    <p class="sr-only" role="status">{{ announcement }}</p>
  </div>
  <div
    v-else-if="monthly === undefined"
    class="monthly-profit-calendar card-shell p-0 md:p-6"
    aria-hidden="true"
  >
    <div class="calendar-body hidden md:flex">
      <div class="calendar-main skeleton h-60"></div>
      <div class="calendar-side">
        <div class="skeleton h-27.5"></div>
        <div class="calendar-period"><div class="skeleton h-23.5"></div></div>
      </div>
    </div>
    <div class="skeleton h-101 md:hidden"></div>
  </div>
  <alert-message v-else variant="info">
    Not enough daily summary history to calculate monthly changes.
  </alert-message>
</template>

<script lang="ts" setup>
import { computed, toRef } from 'vue'
import { useMonthlyProfitSelection } from '../../composables/use-monthly-profit-selection'
import { moveMonth, type MonthlyProfit } from '../../services/monthly-profit'
import { MONTH_STEPS } from '../../services/monthly-profit-texts'
import AlertMessage from '../shared/alert-message.vue'
import DailyProfitLegend from './daily-profit-legend.vue'
import MonthlyProfitFigures from './monthly-profit-figures.vue'
import MonthlyProfitGrid from './monthly-profit-grid.vue'
import MonthlyProfitPanel from './monthly-profit-panel.vue'

const props = defineProps<{ monthly: MonthlyProfit | null | undefined }>()

const { month, preview, previewKey, announcement, select } = useMonthlyProfitSelection(
  toRef(props, 'monthly')
)

const hasMissing = computed(
  () => !!props.monthly?.years.some(year => year.slots.some(slot => slot?.kind === 'missing'))
)
</script>

<style scoped>
.calendar-body {
  flex-wrap: wrap;
  gap: 28px 44px;
}

.calendar-main {
  display: flex;
  flex: 1 1 520px;
  flex-direction: column;
  min-width: 0;
  max-width: 720px;
}

.calendar-side {
  display: flex;
  flex: 1 1 320px;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.calendar-period {
  padding-top: 14px;
  border-top: 1px solid var(--color-hairline);
}

.calendar-head {
  display: flex;
  align-items: baseline;
  column-gap: 16px;
  padding-bottom: 1.25rem;
}

.calendar-heading {
  margin: 0;
  font-size: 1.125rem;
}

.calendar-span {
  font-size: var(--text-2xs);
  color: var(--color-ink-soft);
}
</style>
