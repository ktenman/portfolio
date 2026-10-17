<template>
  <div class="month-panel">
    <p class="month-label">{{ label }}</p>
    <p class="month-change">
      <span class="month-amount" :class="getGainLossClass(month.change)">
        <span>{{ amount[0] }}</span>
        <span class="month-cents">{{ amount[1] }}</span>
      </span>
      <span class="month-percent" :class="percentClass(month.percent)">
        {{ month.percent === null ? '—' : formatPercentChange(month.percent, 1) }}
      </span>
    </p>
    <div class="month-texts">
      <p class="month-detail">{{ month.detail }}</p>
      <p v-if="month.note" class="month-note">{{ month.note }}</p>
    </div>
    <div class="month-buttons">
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
import { SEPARATOR } from '../../services/daily-profit-calendar'
import { percentClass, type ObservedMonth } from '../../services/monthly-profit'
import {
  formatCurrencyChange,
  formatPercentChange,
  getGainLossClass,
  splitCents,
} from '../../utils/formatters'
import DailyProfitArrow from './daily-profit-arrow.vue'

const props = defineProps<{
  month: ObservedMonth
  previous: string | null
  next: string | null
}>()

const emit = defineEmits<{ select: [key: string] }>()

const label = computed(() =>
  props.month.coverage
    ? `${props.month.title}${SEPARATOR}${props.month.coverage}`
    : props.month.title
)

const amount = computed(() => splitCents(formatCurrencyChange(props.month.change)))

const buttons = computed(() => [
  { label: 'Previous month', target: props.previous, direction: 'back' as const },
  { label: 'Next month', target: props.next, direction: 'forward' as const },
])

const go = (target: string | null) => {
  if (target) emit('select', target)
}
</script>

<style scoped>
.month-panel {
  position: relative;
  container-type: inline-size;
}

.month-label {
  margin: 0;
  padding-right: calc(4rem - 8px);
  font-size: var(--text-label);
  font-weight: 500;
  line-height: 1.35rem;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-ink-soft);
}

.month-change {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-height: 1lh;
  margin: 6px 0 0;
  font-size: var(--text-title);
  font-weight: 550;
  line-height: 1.2;
}

.month-cents {
  font-size: 0.5em;
  font-weight: 500;
}

.month-percent {
  font-size: var(--text-control);
}

.month-texts {
  min-height: calc(2lh + 2px);
  margin-top: 6px;
  font-size: var(--text-2xs);
  color: var(--color-ink-soft);
}

.month-detail,
.month-note {
  margin: 0;
}

.month-note {
  margin-top: 2px;
  color: var(--color-notice);
}

.month-buttons {
  position: absolute;
  top: calc((1.35rem - 2rem) / 2);
  right: -8px;
  display: flex;
}

@media (max-width: 768px) {
  .month-label {
    padding-right: calc(5.5rem - 14px);
  }

  .month-buttons {
    top: calc((1.35rem - 2.75rem) / 2);
    right: -14px;
  }
}

@container (min-width: 580px) {
  .month-texts {
    display: flex;
    gap: 12px;
    min-height: 1lh;
  }

  .month-note {
    margin-top: 0;
  }
}
</style>
