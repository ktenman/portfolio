<template>
  <div class="month-figures">
    <div class="figures-body">
      <div>
        <dl class="figure-row">
          <div class="figure">
            <dt class="figure-label">Up months</dt>
            <dd class="figure-value">{{ model.upMonths }}</dd>
          </div>
          <div class="figure">
            <dt class="figure-label">Down months</dt>
            <dd class="figure-value">{{ model.downMonths }}</dd>
          </div>
          <div class="figure">
            <dt class="figure-label">Flat months</dt>
            <dd class="figure-value">
              {{ model.flatMonths }}
              <span class="figure-hint">{{ MONTH_FLAT_HINT }}</span>
            </dd>
          </div>
          <div class="figure">
            <dt class="figure-label">Average month</dt>
            <dd class="figure-value" :class="getGainLossClass(model.averageMonth)">
              <span v-if="average">
                <span>{{ average[0] }}</span>
                <span class="figure-cents">{{ average[1] }}</span>
              </span>
              <span v-else>—</span>
            </dd>
          </div>
        </dl>
        <p v-if="model.excluded" class="figures-excluded">{{ model.excluded }}</p>
      </div>
      <div v-if="extremes.length" class="extreme-buttons">
        <button
          v-for="extreme in extremes"
          :key="extreme.label"
          type="button"
          class="extreme-btn platform-btn md-down:min-h-11"
          @click="emit('select', extreme.month.key)"
        >
          <span class="extreme-content">
            <daily-profit-cell :variant="extreme.variant" class="extreme-swatch" />
            <span>
              {{ extreme.label }}
              <span class="sr-only">by euro profit</span>
            </span>
            <span class="extreme-amount" :class="getGainLossClass(extreme.month.change)">
              {{ formatCurrencyChange(extreme.month.change) }}
            </span>
            <span>{{ formatMonth(extreme.month.key) }}</span>
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { extremeMonths, MONTH_FLAT_HINT, type MonthlyProfit } from '../../services/monthly-profit'
import { formatCurrencyChange, getGainLossClass, splitCents } from '../../utils/formatters'
import { formatMonth } from '../../utils/iso-dates'
import DailyProfitCell from './daily-profit-cell.vue'

const props = defineProps<{ model: MonthlyProfit }>()

const emit = defineEmits<{ select: [key: string] }>()

const average = computed(() =>
  props.model.averageMonth === null
    ? null
    : splitCents(formatCurrencyChange(props.model.averageMonth))
)

const extremes = computed(() => extremeMonths(props.model))
</script>

<style scoped>
.month-figures {
  container-type: inline-size;
}

.figures-body {
  display: grid;
  gap: 19px 44px;
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

.figure-hint,
.figures-excluded {
  font-size: var(--text-2xs);
  font-weight: 400;
  color: var(--color-ink-soft);
}

.figures-excluded {
  margin: 10px 0 0;
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

@container (min-width: 560px) {
  .figure-row {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .extreme-buttons {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    justify-items: start;
  }
}

@container (min-width: 840px) {
  .figures-body {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: end;
  }

  .figure-row {
    grid-template-columns: repeat(4, auto);
    column-gap: 40px;
    justify-content: start;
  }

  .extreme-buttons {
    display: flex;
    gap: 6px;
  }
}

@container calendar (min-width: 1057px) {
  .figure-row {
    grid-template-rows: repeat(2, auto);
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-auto-flow: column;
    gap: 12px 6px;
  }

  .extreme-btn {
    flex-basis: 0;
  }
}
</style>
