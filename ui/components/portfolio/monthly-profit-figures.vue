<template>
  <div class="profit-figures">
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
            <span>{{ extreme.month.label }}</span>
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

<style scoped src="./profit-figures.css"></style>

<style scoped>
.figures-excluded {
  margin: 10px 0 0;
  font-size: var(--text-2xs);
  font-weight: 400;
  color: var(--color-ink-soft);
}
</style>
