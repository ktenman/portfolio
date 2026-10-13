<template>
  <div>
    <div class="compact-head">
      <div class="compact-title">
        <h2 class="compact-heading">Monthly profit</h2>
        <span class="compact-counts">
          {{ monthly.upMonths }} up · {{ monthly.downMonths }} down
        </span>
      </div>
      <p class="compact-span">{{ monthly.span }}</p>
      <div class="dot-block" aria-hidden="true">
        <span
          v-for="(letter, index) in LETTERS"
          :key="index"
          class="dot-letter"
          :style="{ gridColumn: index + FIRST_DOT_COLUMN }"
        >
          {{ letter }}
        </span>
        <template v-for="year in monthly.years" :key="year.year">
          <span class="dot-year">{{ year.year }}</span>
          <span v-for="(slot, index) in year.slots" :key="index" class="dot-place">
            <daily-profit-cell
              v-if="slot"
              :variant="slot.kind === 'missing' ? 'missing' : slot.step"
            />
          </span>
          <span class="dot-total" :class="getGainLossClass(year.percent)">
            {{ year.percent === null ? '—' : formatPercentChange(year.percent, 1) }}
          </span>
        </template>
      </div>
    </div>
    <div class="compact-row">
      <div class="compact-flat">
        <p class="compact-label">Flat months</p>
        <p class="compact-sub">{{ MONTH_FLAT_HINT }}</p>
      </div>
      <span class="compact-value">{{ monthly.flatMonths }}</span>
    </div>
    <div class="compact-row">
      <p class="compact-label">Average month</p>
      <span class="compact-value" :class="getGainLossClass(monthly.averageMonth)">
        <template v-if="average">
          <span>{{ average[0] }}</span>
          <span class="compact-cents">{{ average[1] }}</span>
        </template>
        <template v-else>—</template>
      </span>
    </div>
    <div v-for="extreme in extremes" :key="extreme.label" class="compact-row compact-extreme">
      <div>
        <p class="compact-label">{{ extreme.label }} · {{ formatMonthYear(extreme.month.key) }}</p>
        <p class="compact-sub">{{ extreme.month.closingText }}</p>
      </div>
      <div class="compact-change">
        <span class="compact-amount" :class="getGainLossClass(extreme.month.change)">
          <span>{{ extreme.amount[0] }}</span>
          <span class="compact-cents">{{ extreme.amount[1] }}</span>
        </span>
        <span
          v-if="extreme.month.percent !== null"
          class="compact-percent"
          :class="getGainLossClass(extreme.month.percent)"
        >
          {{ formatPercentChange(extreme.month.percent, 1) }}
        </span>
      </div>
    </div>
    <p v-for="note in notes" :key="note" class="compact-row compact-note">{{ note }}</p>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { extremeMonths, type MonthlyProfit } from '../../services/monthly-profit'
import { excludedNoteOf, MONTH_FLAT_HINT } from '../../services/monthly-profit-texts'
import {
  formatCurrencyChange,
  formatPercentChange,
  getGainLossClass,
  splitCents,
} from '../../utils/formatters'
import { formatMonthYear, MONTHS } from '../../utils/iso-dates'
import DailyProfitCell from './daily-profit-cell.vue'

const props = defineProps<{ monthly: MonthlyProfit }>()

const LETTERS = MONTHS.map(month => month[0])
const FIRST_DOT_COLUMN = 2

const average = computed(() =>
  props.monthly.averageMonth === null
    ? null
    : splitCents(formatCurrencyChange(props.monthly.averageMonth))
)

const extremes = computed(() =>
  extremeMonths(props.monthly).map(extreme => ({
    ...extreme,
    amount: splitCents(formatCurrencyChange(extreme.month.change)),
  }))
)

const notes = computed(() => [
  ...props.monthly.years.flatMap(year => (year.note ? [year.note] : [])),
  ...(props.monthly.excluded ? [excludedNoteOf(props.monthly.excluded)] : []),
])
</script>

<style scoped>
.compact-head {
  padding: 14px 14px 12px;
}

.compact-title {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  column-gap: 8px;
}

.compact-heading {
  margin: 0;
  font-size: 1rem;
}

.compact-counts,
.compact-span,
.dot-letter,
.dot-year {
  font-size: var(--text-label);
  color: var(--color-ink-soft);
}

.compact-span {
  margin: 10px 0;
  text-align: center;
}

.dot-block {
  display: grid;
  grid-template-columns: auto repeat(12, minmax(0, 1fr)) minmax(3.5rem, auto);
  gap: 6px 3px;
  align-items: center;
}

.dot-letter {
  grid-row: 1;
  text-align: center;
}

.dot-year {
  padding-right: 4px;
  font-variant-numeric: tabular-nums;
}

.dot-place {
  display: grid;
  max-width: 18px;
  width: 100%;
  justify-self: center;
}

.dot-total {
  font-size: var(--text-2xs);
  font-variant-numeric: tabular-nums;
  font-weight: 550;
  text-align: right;
  white-space: nowrap;
}

.compact-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  margin: 0;
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

.compact-sub,
.compact-note {
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

.compact-cents,
.compact-percent {
  font-size: var(--text-label);
}

.compact-cents {
  font-weight: 500;
}
</style>
