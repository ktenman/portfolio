<template>
  <div
    ref="strip"
    class="mb-3 flex gap-1.5 overflow-x-auto md-down:-mr-3 md-down:pr-3 md-down:[scrollbar-width:none]"
    role="group"
    aria-label="Festival day"
    :aria-busy="!days.length || undefined"
  >
    <button
      v-for="day in days"
      :key="day"
      type="button"
      class="flex min-w-13 flex-1 cursor-pointer flex-col items-center rounded-container border px-2 py-1.5 transition-colors"
      :class="
        day === model
          ? 'border-brass bg-brass-wash text-brass-deep inset-ring inset-ring-brass'
          : [
              'border-transparent bg-surface-sunken hover:border-hairline-strong',
              day < today ? 'text-ink-soft' : 'text-ink',
            ]
      "
      :aria-label="fullDate(day)"
      :aria-pressed="day === model"
      :aria-current="day === today ? 'date' : undefined"
      @click="model = clearable && day === model ? null : day"
    >
      <span class="text-label" :class="{ 'text-ink-soft': day !== model }">
        {{ day === today ? 'Today' : WEEKDAYS[mondayIndex(day)] }}
      </span>
      <span class="text-xl leading-6 font-semibold tabular-nums">
        {{ Number(day.slice(8)) }}
      </span>
    </button>
    <template v-if="!days.length">
      <span
        v-for="i in 17"
        :key="i"
        class="skeleton h-14 min-w-13 flex-1 rounded-container"
        data-testid="poff-day-loading"
      />
    </template>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, useTemplateRef, watch } from 'vue'
import { mondayIndex, WEEKDAYS } from '../../utils/iso-dates'
import { revealInRow } from '../../utils/dom'
import { fullDate } from '../../utils/poff'

const props = defineProps<{ days: string[]; today: string; clearable?: boolean }>()

const model = defineModel<string | null>({ required: true })
const strip = useTemplateRef<HTMLElement>('strip')

const center = () =>
  revealInRow(
    strip.value,
    model.value ? '[aria-pressed="true"]' : '[aria-current="date"]',
    'center'
  )

onMounted(center)
watch(() => props.days.length > 0, center, { flush: 'post' })
</script>
