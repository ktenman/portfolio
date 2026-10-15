<template>
  <div class="month-figures">
    <dl class="figure-row">
      <div class="figure">
        <dt class="figure-label">Up months</dt>
        <dd class="figure-value">{{ monthly.upMonths }}</dd>
      </div>
      <div class="figure">
        <dt class="figure-label">Down months</dt>
        <dd class="figure-value">{{ monthly.downMonths }}</dd>
      </div>
      <div class="figure">
        <dt class="figure-label">Flat months</dt>
        <dd class="figure-value">
          {{ monthly.flatMonths }}
          <span class="figure-hint">{{ MONTH_FLAT_HINT }}</span>
        </dd>
      </div>
      <div class="figure">
        <dt class="figure-label">Average month</dt>
        <dd class="figure-value" :class="getGainLossClass(monthly.averageMonth)">
          <span v-if="average">
            <span>{{ average[0] }}</span>
            <span class="figure-cents">{{ average[1] }}</span>
          </span>
          <template v-else>—</template>
        </dd>
      </div>
    </dl>
    <div v-if="extremes.length" class="extreme-buttons">
      <button
        v-for="extreme in extremes"
        :key="extreme.label"
        type="button"
        class="extreme-btn platform-btn md-down:min-h-11"
        aria-description="Ranked by euro profit"
        @click="emit('select', extreme.month.key)"
      >
        <span class="extreme-content">
          <daily-profit-cell :variant="extreme.variant" class="extreme-swatch" />
          <span>{{ extreme.label }}</span>
          <span class="extreme-amount" :class="getGainLossClass(extreme.month.change)">
            {{ formatCurrencyChange(extreme.month.change) }}
          </span>
          <span>{{ formatMonthYear(extreme.month.key) }}</span>
        </span>
      </button>
    </div>
    <p v-if="monthly.excluded" class="figures-note">{{ excludedNoteOf(monthly.excluded) }}</p>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { extremeMonths, type MonthlyProfit } from '../../services/monthly-profit'
import { excludedNoteOf, MONTH_FLAT_HINT } from '../../services/monthly-profit-texts'
import { formatCurrencyChange, getGainLossClass, splitCents } from '../../utils/formatters'
import { formatMonthYear } from '../../utils/iso-dates'
import DailyProfitCell from './daily-profit-cell.vue'

const props = defineProps<{ monthly: MonthlyProfit }>()

const emit = defineEmits<{ select: [key: string] }>()

const average = computed(() =>
  props.monthly.averageMonth === null
    ? null
    : splitCents(formatCurrencyChange(props.monthly.averageMonth))
)

const extremes = computed(() => extremeMonths(props.monthly))
</script>

<style scoped>
.month-figures {
  display: grid;
  gap: 16px;
}

.figure-row {
  display: grid;
  grid-template-columns: repeat(4, auto);
  gap: 16px;
  justify-content: space-between;
  margin: 0;
}

.figure-label {
  font-size: var(--text-label);
  font-weight: 500;
  line-height: 1.35rem;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--color-ink-soft);
}

.figure-value {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 550;
  line-height: 1.2;
  white-space: nowrap;
}

.figure-hint {
  font-size: var(--text-2xs);
  font-weight: 400;
  color: var(--color-ink-soft);
}

.figure-cents {
  font-size: var(--text-label);
  font-weight: 500;
}

.extreme-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.extreme-btn {
  flex: 1 1 auto;
  padding-inline: 14px 8px;
}

.extreme-content {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
}

.extreme-swatch {
  align-self: center;
  width: 9px;
}

.extreme-amount {
  font-weight: 550;
}

.figures-note {
  margin: 0;
  font-size: var(--text-2xs);
  color: var(--color-ink-soft);
}
</style>
