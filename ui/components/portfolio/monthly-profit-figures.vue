<template>
  <div class="monthly-profit-figures">
    <dl class="monthly-profit-figure-row">
      <div class="monthly-profit-figure">
        <dt class="monthly-profit-figure-label">Up months</dt>
        <dd class="monthly-profit-figure-value">{{ calendar.upMonths }}</dd>
      </div>
      <div class="monthly-profit-figure">
        <dt class="monthly-profit-figure-label">Down months</dt>
        <dd class="monthly-profit-figure-value">{{ calendar.downMonths }}</dd>
      </div>
      <div class="monthly-profit-figure">
        <dt class="monthly-profit-figure-label">Flat months</dt>
        <dd class="monthly-profit-figure-value">
          {{ calendar.flatMonths }}
          <span class="monthly-profit-hint">under ±{{ MONTHLY_CHANGE_STEPS[0] }}%</span>
        </dd>
      </div>
      <div class="monthly-profit-figure">
        <dt class="monthly-profit-figure-label">Average month</dt>
        <dd class="monthly-profit-figure-value" :class="getGainLossClass(calendar.averageMonth)">
          <span>
            <span
              v-for="(part, index) in average"
              :key="index"
              :class="{ 'monthly-profit-figure-cents': index === 1 }"
            >
              {{ part }}
            </span>
          </span>
        </dd>
      </div>
    </dl>
    <div v-if="extremes.length" class="monthly-profit-extremes">
      <button
        v-for="extreme in extremes"
        :key="extreme.label"
        type="button"
        class="monthly-profit-extreme-btn platform-btn md-down:min-h-11"
        :aria-label="`${extreme.label}, ranked by euro profit. ${extreme.month.name}`"
        @click="emit('select', extreme.month.key)"
      >
        <span class="monthly-profit-swatch" :class="extreme.month.step" aria-hidden="true" />
        <span>{{ extreme.label }}</span>
        <span class="monthly-profit-extreme-amount" :class="getGainLossClass(extreme.month.change)">
          {{ formatCurrencyChange(extreme.month.change) }}
        </span>
        <span>{{ formatMonth(extreme.month.key) }}</span>
      </button>
    </div>
    <p v-if="calendar.excludedMonths" class="monthly-profit-note">
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
  MONTHLY_CHANGE_STEPS,
  type MonthlyProfit,
} from '../../services/monthly-profit'
import { formatCurrencyChange, getGainLossClass, splitCents } from '../../utils/formatters'

const props = defineProps<{ calendar: MonthlyProfit }>()
const emit = defineEmits<{ select: [key: string] }>()

const average = computed(() =>
  splitCents(
    props.calendar.averageMonth === null ? '—' : formatCurrencyChange(props.calendar.averageMonth)
  )
)
const extremes = computed(() =>
  [
    { label: 'Best month', month: props.calendar.best },
    { label: 'Worst month', month: props.calendar.worst },
  ].flatMap(extreme => (extreme.month ? [{ ...extreme, month: extreme.month }] : []))
)
</script>
