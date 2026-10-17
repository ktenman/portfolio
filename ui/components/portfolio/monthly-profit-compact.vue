<template>
  <div>
    <div class="compact-head">
      <div class="compact-title">
        <h2 class="compact-heading">Monthly profit</h2>
        <span class="compact-counts">{{ model.upMonths }} up · {{ model.downMonths }} down</span>
      </div>
      <p class="compact-span">{{ model.span }}</p>
      <div class="dot-grid" aria-hidden="true">
        <span />
        <span v-for="(letter, index) in LETTERS" :key="index" class="dot-letter">{{ letter }}</span>
        <span />
        <template v-for="year in years" :key="year.year">
          <span class="dot-year">{{ year.year }}</span>
          <template v-for="(slot, index) in year.slots" :key="index">
            <daily-profit-cell
              v-if="slot"
              :variant="slot.kind === 'observed' ? slot.step : 'missing'"
            />
            <span v-else />
          </template>
          <span class="dot-total" :class="percentClass(year.percent)">{{ year.text }}</span>
        </template>
      </div>
      <div
        v-if="notes.length"
        class="year-notes mt-2.5 text-center text-label leading-[1.4] text-ink-soft"
        aria-hidden="true"
      >
        <p v-for="note in notes" :key="note" class="m-0">{{ note }}</p>
      </div>
      <span v-for="year in years" :key="year.year" class="sr-only">{{ year.name }}</span>
    </div>
    <div class="compact-row">
      <div class="compact-flat">
        <p class="compact-label">Flat months</p>
        <p class="compact-sub">{{ MONTH_FLAT_HINT }}</p>
      </div>
      <span class="compact-value">{{ model.flatMonths }}</span>
    </div>
    <div class="compact-row">
      <p class="compact-label">Average month</p>
      <span class="compact-value" :class="getGainLossClass(model.averageMonth)">
        <template v-if="average">
          <span>{{ average[0] }}</span>
          <span class="compact-cents">{{ average[1] }}</span>
        </template>
        <template v-else>—</template>
      </span>
    </div>
    <div v-if="model.excluded" class="compact-row">
      <p class="compact-sub">{{ model.excluded }}</p>
    </div>
    <div v-for="extreme in extremes" :key="extreme.label" class="compact-row compact-extreme">
      <div>
        <p class="compact-label">
          {{ extreme.label }}
          <span class="sr-only">by euro profit</span>
          ·
          {{ extreme.month.label }}
        </p>
        <p class="compact-sub">{{ extreme.month.closing }}</p>
      </div>
      <div class="compact-change">
        <span class="compact-amount" :class="getGainLossClass(extreme.month.change)">
          <span>{{ extreme.amount[0] }}</span>
          <span class="compact-cents">{{ extreme.amount[1] }}</span>
        </span>
        <span class="compact-percent" :class="percentClass(extreme.month.percent)">
          {{ formatMonthChange(extreme.month.percent) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  extremeMonths,
  formatMonthChange,
  MONTH_FLAT_HINT,
  percentClass,
  type MonthlyProfit,
} from '../../services/monthly-profit'
import { yearNotes, type MonthlyYear } from '../../services/monthly-profit-years'
import { formatCurrencyChange, getGainLossClass, splitCents } from '../../utils/formatters'
import { MONTHS } from '../../utils/iso-dates'
import DailyProfitCell from './daily-profit-cell.vue'

const props = defineProps<{ model: MonthlyProfit; years: MonthlyYear[] }>()

const LETTERS = MONTHS.map(month => month[0])

const notes = computed(() => yearNotes(props.years))

const average = computed(() =>
  props.model.averageMonth === null
    ? null
    : splitCents(formatCurrencyChange(props.model.averageMonth))
)

const extremes = computed(() =>
  extremeMonths(props.model).map(extreme => ({
    ...extreme,
    amount: splitCents(formatCurrencyChange(extreme.month.change)),
  }))
)
</script>

<style scoped src="./profit-compact.css"></style>

<style scoped>
.compact-head {
  padding: 14px 14px 12px;
}

.compact-span {
  margin: 8px 0 0;
  font-weight: 500;
  text-align: center;
}

.dot-grid {
  display: grid;
  grid-template-columns: auto repeat(12, minmax(0, 18px)) auto;
  gap: 4px;
  align-items: center;
  justify-content: center;
  margin-top: 12px;
  font-size: var(--text-label);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.dot-letter {
  justify-self: center;
  color: var(--color-ink-soft);
}

.dot-year {
  padding-right: 4px;
  color: var(--color-ink-soft);
}

.dot-total {
  justify-self: end;
  padding-left: 8px;
  white-space: nowrap;
}
</style>
