<template>
  <div ref="strip" class="mb-3 flex gap-1.5 overflow-x-auto" role="group" aria-label="Festival day">
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
      @click="model = day"
    >
      <span class="text-label" :class="{ 'text-ink-soft': day !== model }">
        {{ day === today ? 'Today' : WEEKDAYS[mondayIndex(day)] }}
      </span>
      <span class="text-xl leading-6 font-semibold tabular-nums">
        {{ Number(day.slice(8)) }}
      </span>
    </button>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, useTemplateRef } from 'vue'
import { mondayIndex, WEEKDAYS } from '../../utils/iso-dates'
import { fullDate } from '../../utils/poff'

defineProps<{ days: string[]; today: string }>()

const model = defineModel<string>({ required: true })
const strip = useTemplateRef<HTMLElement>('strip')

onMounted(() =>
  strip.value
    ?.querySelector('[aria-pressed="true"]')
    ?.scrollIntoView({ block: 'nearest', inline: 'center' })
)
</script>
