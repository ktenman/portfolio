<template>
  <div v-if="calendar && month" class="monthly-profit-calendar card-shell p-0 md:p-6">
    <div class="monthly-profit-body hidden md:flex">
      <div class="monthly-profit-main">
        <div class="monthly-profit-head">
          <h2 class="monthly-profit-heading">Monthly profit</h2>
          <p class="monthly-profit-span">{{ calendar.span }}</p>
        </div>
        <monthly-profit-grid
          :calendar="calendar"
          :selected="selectedKey"
          :previewed="previewKey"
          @select="select"
          @preview="previewKey = $event"
        />
        <div class="monthly-profit-coverage">
          <template v-for="year in calendar.years" :key="year.year">
            <p v-for="note in year.coverage" :key="note">{{ year.year }} · {{ note }}</p>
            <p v-if="year.percent === null && year.change !== null">
              {{ year.year }} · Percentage unavailable
            </p>
          </template>
        </div>
        <monthly-profit-legend :missing="missing" />
      </div>
      <div class="monthly-profit-side">
        <monthly-profit-panel
          :month="preview ?? month"
          :previous="moveMonth(calendar, selectedKey, -1)"
          :next="moveMonth(calendar, selectedKey, 1)"
          @select="select"
        />
        <monthly-profit-figures :calendar="calendar" @select="select" />
      </div>
    </div>
    <monthly-profit-compact class="md:hidden" :calendar="calendar" />
    <p class="sr-only" role="status">{{ announcement }}</p>
  </div>
  <div v-else class="monthly-profit-calendar card-shell p-4">
    Not enough daily summary history to calculate monthly changes.
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { useMonthlyProfitCalendar } from '../../composables/use-monthly-profit-calendar'
import { moveMonth, type MonthlyProfit } from '../../services/monthly-profit'
import MonthlyProfitCompact from './monthly-profit-compact.vue'
import MonthlyProfitFigures from './monthly-profit-figures.vue'
import MonthlyProfitGrid from './monthly-profit-grid.vue'
import MonthlyProfitLegend from './monthly-profit-legend.vue'
import MonthlyProfitPanel from './monthly-profit-panel.vue'

const props = defineProps<{ calendar: MonthlyProfit | null }>()

const { month, preview, selectedKey, previewKey, announcement, select } =
  useMonthlyProfitCalendar(props)

const missing = computed(
  () =>
    props.calendar?.years.some(year => year.slots.some(slot => slot.kind === 'missing')) ?? false
)
</script>
