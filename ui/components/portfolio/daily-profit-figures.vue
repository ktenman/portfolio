<template>
  <div class="profit-figures">
    <div class="figures-body">
      <dl class="figure-row">
        <div class="figure">
          <dt class="figure-label">Up days</dt>
          <dd class="figure-value">{{ calendar.upDays }}</dd>
        </div>
        <div class="figure">
          <dt class="figure-label">Down days</dt>
          <dd class="figure-value">{{ calendar.downDays }}</dd>
        </div>
        <div class="figure">
          <dt class="figure-label">Flat days</dt>
          <dd class="figure-value">
            {{ calendar.flatDays }}
            <span class="figure-hint">{{ FLAT_HINT }}</span>
          </dd>
        </div>
        <div class="figure">
          <dt class="figure-label">Average day</dt>
          <dd class="figure-value" :class="getGainLossClass(calendar.averageDay)">
            <span>
              <span>{{ average[0] }}</span>
              <span class="figure-cents">{{ average[1] }}</span>
            </span>
          </dd>
        </div>
      </dl>
      <div v-if="extremes.length" class="extreme-buttons">
        <button
          v-for="extreme in extremes"
          :key="extreme.label"
          type="button"
          class="extreme-btn platform-btn md-down:min-h-11"
          @click="emit('select', extreme.day.date)"
        >
          <span class="extreme-content">
            <daily-profit-cell :variant="extreme.variant" class="extreme-swatch" />
            <span>{{ extreme.label }}</span>
            <span class="extreme-amount" :class="getGainLossClass(extreme.day.change)">
              {{ formatCurrencyChange(extreme.day.change) }}
            </span>
            <span>{{ formatDayMonth(extreme.day.date) }}</span>
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import {
  extremeDays,
  FLAT_HINT,
  type DailyProfitCalendar,
} from '../../services/daily-profit-calendar'
import { formatCurrencyChange, getGainLossClass, splitCents } from '../../utils/formatters'
import { formatDayMonth } from '../../utils/iso-dates'
import DailyProfitCell from './daily-profit-cell.vue'

const props = defineProps<{ calendar: DailyProfitCalendar }>()

const emit = defineEmits<{ select: [date: string] }>()

const average = computed(() => splitCents(formatCurrencyChange(props.calendar.averageDay)))

const extremes = computed(() => extremeDays(props.calendar))
</script>

<style scoped src="./profit-figures.css"></style>
