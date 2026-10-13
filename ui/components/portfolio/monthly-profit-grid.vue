<template>
  <div
    ref="grid"
    class="monthly-profit-grid"
    role="application"
    aria-label="Monthly profit calendar"
    tabindex="0"
    :aria-describedby="selectedId"
    @pointerleave="emit('preview', null)"
    @keydown="onKeydown"
  >
    <span class="monthly-profit-grid-corner" aria-hidden="true" />
    <span
      v-for="label in MONTHS"
      :key="label"
      class="monthly-profit-month-label"
      aria-hidden="true"
    >
      {{ label }}
    </span>
    <span class="monthly-profit-month-label monthly-profit-year-heading" aria-hidden="true">
      Year
    </span>
    <template v-for="year in calendar.years" :key="year.year">
      <span class="monthly-profit-year-label">{{ year.year }}</span>
      <span
        v-for="slot in year.slots"
        :key="slot.key"
        class="monthly-profit-cell"
        :class="[
          slot.kind === 'observed' ? slot.month.step : slot.kind,
          {
            selected: slot.key === selected,
            previewed: slot.key === previewed && slot.key !== selected,
            selectable: slot.kind === 'observed',
          },
        ]"
        :data-month="slot.key"
        v-bind="cellAttributes(slot)"
        @click="onClick(slot)"
        @pointerenter="onEnter(slot, $event)"
      >
        <span
          v-if="slot.kind === 'observed'"
          class="monthly-profit-cell-value"
          :style="{ '--characters': formatMonthlyPercent(slot.month.percent, true).length }"
        >
          {{ formatMonthlyPercent(slot.month.percent, true) }}
        </span>
      </span>
      <span class="monthly-profit-year-total" role="img" :aria-label="year.name">
        <span :class="getGainLossClass(year.percent)">
          {{ formatMonthlyPercent(year.percent) }}
        </span>
      </span>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { ref, useId } from 'vue'
import {
  formatMonthlyPercent,
  moveMonth,
  type MonthlyProfit,
  type ProfitMonthSlot,
} from '../../services/monthly-profit'
import { getGainLossClass } from '../../utils/formatters'

const props = defineProps<{
  calendar: MonthlyProfit
  selected: string
  previewed: string | null
}>()

const emit = defineEmits<{ select: [key: string]; preview: [key: string | null] }>()

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
const KEY_STEPS = new Map([
  ['ArrowLeft', -1],
  ['ArrowRight', 1],
  ['ArrowUp', 12],
  ['ArrowDown', -12],
])
const grid = ref<HTMLElement | null>(null)
const selectedId = useId()

const cellAttributes = (slot: ProfitMonthSlot) => {
  if (slot.kind === 'empty') return { 'aria-hidden': 'true' as const }
  const name = slot.kind === 'observed' ? slot.month.name : slot.name
  const current =
    slot.key === props.selected ? { id: selectedId, 'aria-current': 'true' as const } : {}
  return { role: 'img', 'aria-label': name, title: name, ...current }
}

const onClick = (slot: ProfitMonthSlot) => {
  if (slot.kind !== 'observed') return
  grid.value?.focus()
  emit('select', slot.key)
}

const onEnter = (slot: ProfitMonthSlot, event: PointerEvent) => {
  if (slot.kind === 'observed' && event.pointerType !== 'touch') emit('preview', slot.key)
}

const onKeydown = (event: KeyboardEvent) => {
  const step = KEY_STEPS.get(event.key)
  if (!step || event.altKey || event.ctrlKey || event.metaKey) return
  event.preventDefault()
  const key = moveMonth(props.calendar, props.selected, step)
  if (key) emit('select', key)
}
</script>
