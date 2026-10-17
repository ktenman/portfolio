<template>
  <div v-if="model && month" class="monthly-profit-calendar card-shell p-0 md:p-6">
    <alert-message v-if="status === 'error'" variant="warning" class="m-3.5 md:mx-0 md:mt-0">
      <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
        <span>Could not refresh the monthly history. Showing data as of {{ asOf }}.</span>
        <app-button size="sm" @click="emit('retry')">Try again</app-button>
      </div>
    </alert-message>
    <div class="calendar-body hidden md:flex">
      <div class="calendar-main">
        <div class="calendar-head">
          <h2 class="calendar-heading">Monthly profit</h2>
          <span class="calendar-span">{{ model.span }}</span>
        </div>
        <monthly-profit-grid
          :model="model"
          :years="years"
          :selected="month.key"
          :previewed="previewKey"
          @select="select"
          @preview="previewKey = $event"
        />
        <daily-profit-legend :missing="hasMissingMonth(years)" :steps="MONTH_STEPS" />
      </div>
      <div class="calendar-side">
        <monthly-profit-panel
          class="calendar-day"
          :month="preview ?? month"
          :previous="moveMonth(model, month.key, -1)"
          :next="moveMonth(model, month.key, 1)"
          @select="select"
        />
        <monthly-profit-figures class="calendar-period" :model="model" @select="select" />
      </div>
    </div>
    <monthly-profit-compact class="md:hidden" :model="model" :years="years" />
    <p class="sr-only" role="status">{{ announcement }}</p>
  </div>
  <alert-message v-else-if="model === null" variant="info">{{ NOT_ENOUGH_HISTORY }}</alert-message>
  <div v-else class="monthly-profit-calendar card-shell p-0 md:p-6" aria-hidden="true">
    <div class="calendar-body hidden md:flex">
      <div class="calendar-main skeleton min-h-75"></div>
      <div class="calendar-side">
        <div class="calendar-day skeleton h-22 @min-[800px]:h-27.5"></div>
        <div class="calendar-period">
          <div
            class="skeleton h-23.5 md-down:h-27 @min-[945px]:@max-[1057px]:h-11.5 @min-[1057px]:h-38"
          ></div>
        </div>
      </div>
    </div>
    <div class="skeleton h-101 md:hidden"></div>
  </div>
</template>

<script lang="ts" setup>
import { computed, toRef } from 'vue'
import { useMonthlyProfit } from '../../composables/use-monthly-profit'
import type { HistoryStatus } from '../../composables/use-summary-calendar-query'
import {
  moveMonth,
  MONTH_STEPS,
  NOT_ENOUGH_HISTORY,
  type MonthlyProfit,
} from '../../services/monthly-profit'
import { hasMissingMonth, monthlyYears } from '../../services/monthly-profit-years'
import { formatFullDate } from '../../utils/iso-dates'
import AlertMessage from '../shared/alert-message.vue'
import AppButton from '../shared/app-button.vue'
import DailyProfitLegend from './daily-profit-legend.vue'
import MonthlyProfitCompact from './monthly-profit-compact.vue'
import MonthlyProfitFigures from './monthly-profit-figures.vue'
import MonthlyProfitGrid from './monthly-profit-grid.vue'
import MonthlyProfitPanel from './monthly-profit-panel.vue'

const props = defineProps<{ model: MonthlyProfit | null | undefined; status: HistoryStatus }>()

const emit = defineEmits<{ retry: [] }>()

const { month, preview, previewKey, announcement, select } = useMonthlyProfit(
  toRef(() => props.model)
)

const years = computed(() => (props.model ? monthlyYears(props.model) : []))

const asOf = computed(() =>
  props.model ? formatFullDate(props.model.months[props.model.months.length - 1].row.date) : ''
)
</script>

<style scoped>
.calendar-body {
  flex-wrap: wrap;
  gap: 28px 44px;
  container: calendar / inline-size;
}

.calendar-main {
  display: flex;
  flex: 1 1 100%;
  flex-direction: column;
  min-width: 0;
}

.calendar-side {
  display: flex;
  flex: 1 1 100%;
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
  align-items: center;
  column-gap: 16px;
  padding-bottom: 1.25rem;
}

.calendar-heading {
  margin: 0;
  font-size: 1.125rem;
}

.calendar-span {
  font-size: var(--text-base);
  font-weight: 500;
  white-space: nowrap;
}

@container (min-width: 800px) and (max-width: 944.98px) {
  .calendar-side {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 412px;
    gap: 0 44px;
    padding-top: 20px;
    border-top: 1px solid var(--color-hairline);
  }

  .calendar-period {
    align-self: start;
    padding-top: 0;
    border-top: 0;
  }
}

@container (min-width: 945px) {
  .calendar-main {
    flex: 1 1 0;
  }
}

@container (min-width: 945px) and (max-width: 1056.98px) {
  .calendar-side {
    display: contents;
  }

  .calendar-day {
    flex: 0 0 300px;
  }

  .calendar-period {
    flex: 1 1 100%;
  }
}

@container (min-width: 1057px) {
  .calendar-side {
    flex: 0 0 412px;
  }

  .calendar-period {
    margin-top: auto;
  }
}
</style>
