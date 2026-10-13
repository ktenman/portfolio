<template>
  <div class="monthly-profit-panel" :data-month="month.key">
    <p class="monthly-profit-label">{{ formatMonth(month.key, true) }}</p>
    <p class="monthly-profit-change">
      <span class="monthly-profit-amount" :class="amountClass">
        <span
          v-for="(part, index) in amount"
          :key="index"
          :class="{ 'monthly-profit-cents': index === 1 }"
        >
          {{ part }}
        </span>
      </span>
      <span class="monthly-profit-percent" :class="getGainLossClass(month.percent)">
        {{ formatMonthlyPercent(month.percent) }}
      </span>
    </p>
    <div class="monthly-profit-texts">
      <p class="monthly-profit-detail">
        <template v-if="month.rank">
          {{ month.rank }}
          <span class="sr-only">by euro profit</span>
          ·
        </template>
        {{ month.closingLabel }} · XIRR
        {{ formatSignedPercent(month.closing.xirrAnnualReturn * 100) }}
      </p>
      <p v-for="note in month.coverage" :key="note" class="monthly-profit-note">{{ note }}</p>
      <p v-if="month.percent === null" class="monthly-profit-note">Percentage unavailable</p>
    </div>
    <div class="monthly-profit-buttons">
      <daily-profit-arrow
        v-for="button in buttons"
        :key="button.label"
        :label="button.label"
        :direction="button.direction"
        :disabled="!button.target"
        @press="go(button.target)"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { formatMonth, formatMonthlyPercent, type ProfitMonth } from '../../services/monthly-profit'
import {
  formatCurrencyChange,
  formatSignedPercent,
  getGainLossClass,
  splitCents,
} from '../../utils/formatters'
import DailyProfitArrow from './daily-profit-arrow.vue'

const props = defineProps<{ month: ProfitMonth; previous: string | null; next: string | null }>()
const emit = defineEmits<{ select: [key: string] }>()

const amount = computed(() =>
  splitCents(Number.isFinite(props.month.change) ? formatCurrencyChange(props.month.change) : '—')
)
const amountClass = computed(() =>
  Number.isFinite(props.month.change) ? getGainLossClass(props.month.change) : ''
)
const buttons = computed(() => [
  { label: 'Previous month', target: props.previous, direction: 'back' as const },
  { label: 'Next month', target: props.next, direction: 'forward' as const },
])

const go = (target: string | null) => {
  if (target) emit('select', target)
}
</script>
