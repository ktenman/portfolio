<template>
  <div class="day-figures">
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

<style scoped>
.day-figures {
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
