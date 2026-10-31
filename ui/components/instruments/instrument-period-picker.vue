<template>
  <div ref="root" class="relative max-sm:static">
    <div class="flex" role="group" aria-label="Price change period">
      <button
        ref="toggleButton"
        :class="[
          PERIOD_CELL,
          'inline-flex min-w-16.75 items-center justify-center gap-1 not-data-active:text-ink',
        ]"
        :data-active="open || undefined"
        :aria-expanded="open"
        aria-controls="period-picker"
        type="button"
        @click="toggle"
      >
        {{ selected }}
        <svg
          aria-hidden="true"
          class="size-3 shrink-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="m6 9 6 6 6-6" />
        </svg>
      </button>
    </div>
    <period-popover
      v-if="open"
      class="w-max max-sm:inset-x-0 max-sm:w-auto"
      :selected="selected"
      @change="emit('select', $event)"
      @pick="pick"
    >
      <div class="flex" role="group" aria-label="Common periods">
        <button
          v-for="range in SHORTLIST"
          :key="range"
          :class="[PERIOD_CELL, 'flex-1 max-sm:px-1']"
          :data-active="range === selected || undefined"
          :aria-pressed="range === selected"
          type="button"
          @click="pick(range)"
        >
          {{ range }}
        </button>
      </div>
    </period-popover>
  </div>
</template>

<script setup lang="ts">
import { useTemplateRef } from 'vue'
import { PERIOD_CELL, SHORTLIST, usePeriodPopover } from '../../composables/use-time-range'
import type { TimeRange } from '../../models/generated/domain-models'
import PeriodPopover from '../portfolio/period-popover.vue'

defineProps<{
  selected: TimeRange
}>()

const emit = defineEmits<{
  select: [range: TimeRange]
}>()

const root = useTemplateRef('root')
const toggleButton = useTemplateRef('toggleButton')
const { open, close, toggle } = usePeriodPopover(root, toggleButton)

const pick = (range: TimeRange) => {
  emit('select', range)
  close()
}
</script>
