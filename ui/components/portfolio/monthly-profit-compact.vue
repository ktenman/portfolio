<template>
  <div class="monthly-profit-compact">
    <div class="monthly-profit-compact-head">
      <div class="monthly-profit-compact-title">
        <h2 class="monthly-profit-heading">Monthly profit</h2>
        <span class="monthly-profit-compact-counts">
          {{ calendar.upMonths }} up · {{ calendar.downMonths }} down
        </span>
      </div>
      <p class="monthly-profit-span">{{ calendar.span }}</p>
      <div class="monthly-profit-dot-grid">
        <span aria-hidden="true" />
        <span
          v-for="(label, index) in MONTHS"
          :key="index"
          class="monthly-profit-month-label"
          aria-hidden="true"
        >
          {{ label }}
        </span>
        <span aria-hidden="true" />
        <template v-for="year in calendar.years" :key="year.year">
          <span class="monthly-profit-year-label">{{ year.year }}</span>
          <span
            v-for="slot in year.slots"
            :key="slot.key"
            class="monthly-profit-dot"
            :class="slot.kind === 'observed' ? slot.month.step : slot.kind"
            aria-hidden="true"
          />
          <span
            class="monthly-profit-year-total"
            role="img"
            :aria-label="year.name"
            :class="getGainLossClass(year.percent)"
          >
            {{ formatMonthlyPercent(year.percent) }}
          </span>
        </template>
      </div>
      <div class="monthly-profit-coverage">
        <template v-for="year in calendar.years" :key="year.year">
          <p v-for="note in year.coverage" :key="note">{{ year.year }} · {{ note }}</p>
          <p v-if="year.percent === null && year.change !== null">
            {{ year.year }} · Percentage unavailable
          </p>
        </template>
        <p v-if="missing">No daily summary: dashed months</p>
      </div>
    </div>
    <div class="monthly-profit-compact-row">
      <div class="monthly-profit-compact-flat">
        <p class="monthly-profit-figure-label">Flat months</p>
        <p class="monthly-profit-hint">under ±{{ MONTHLY_CHANGE_STEPS[0] }}%</p>
      </div>
      <span class="monthly-profit-compact-value">{{ calendar.flatMonths }}</span>
    </div>
    <div class="monthly-profit-compact-row">
      <p class="monthly-profit-figure-label">Average month</p>
      <span class="monthly-profit-compact-value" :class="getGainLossClass(calendar.averageMonth)">
        <span
          v-for="(part, index) in average"
          :key="index"
          :class="{ 'monthly-profit-figure-cents': index === 1 }"
        >
          {{ part }}
        </span>
      </span>
    </div>
    <div
      v-for="extreme in extremes"
      :key="extreme.label"
      class="monthly-profit-compact-row monthly-profit-compact-extreme"
    >
      <div class="monthly-profit-compact-description">
        <p class="monthly-profit-figure-label">
          {{ extreme.label }} · {{ formatMonth(extreme.month.key) }}
          <span class="sr-only">by euro profit</span>
        </p>
        <p class="monthly-profit-hint">{{ extreme.month.closingLabel }}</p>
        <p v-for="note in extreme.month.coverage" :key="note" class="monthly-profit-hint">
          {{ note }}
        </p>
        <p v-if="extreme.month.percent === null" class="monthly-profit-hint">
          Percentage unavailable
        </p>
      </div>
      <div class="monthly-profit-compact-change">
        <span class="monthly-profit-compact-amount" :class="getGainLossClass(extreme.month.change)">
          <span
            v-for="(part, index) in extreme.amount"
            :key="index"
            :class="{ 'monthly-profit-figure-cents': index === 1 }"
          >
            {{ part }}
          </span>
        </span>
        <span
          class="monthly-profit-compact-percent"
          :class="getGainLossClass(extreme.month.percent)"
        >
          {{ formatMonthlyPercent(extreme.month.percent) }}
        </span>
      </div>
    </div>
    <p v-if="calendar.excludedMonths" class="monthly-profit-compact-excluded monthly-profit-note">
      {{ calendar.excludedMonths }} interval{{ calendar.excludedMonths === 1 ? '' : 's' }} excluded
      from monthly statistics because
      {{ calendar.excludedMonths === 1 ? 'it covers' : 'they cover' }}
      multiple months.
    </p>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  formatMonth,
  formatMonthlyPercent,
  MONTHLY_CHANGE_STEPS,
  type MonthlyProfit,
} from '../../services/monthly-profit'
import { formatCurrencyChange, getGainLossClass, splitCents } from '../../utils/formatters'

const props = defineProps<{ calendar: MonthlyProfit }>()
const MONTHS = ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D']

const average = computed(() =>
  splitCents(
    props.calendar.averageMonth === null ? '—' : formatCurrencyChange(props.calendar.averageMonth)
  )
)
const missing = computed(() =>
  props.calendar.years.some(year => year.slots.some(slot => slot.kind === 'missing'))
)
const extremes = computed(() =>
  [
    { label: 'Best month', month: props.calendar.best },
    { label: 'Worst month', month: props.calendar.worst },
  ].flatMap(extreme =>
    extreme.month
      ? [
          {
            ...extreme,
            month: extreme.month,
            amount: splitCents(formatCurrencyChange(extreme.month.change)),
          },
        ]
      : []
  )
)
</script>
