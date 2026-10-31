<template>
  <div ref="root" class="relative max-sm:static">
    <div class="flex" role="group" aria-label="Price change period">
      <button
        ref="toggleButton"
        class="form-select form-select-sm relative cursor-pointer bg-position-[right_0.25rem_center] pr-5.5 text-left hover:bg-surface-hover md-down:before:absolute md-down:before:inset-x-0 md-down:before:top-1/2 md-down:before:h-11 md-down:before:-translate-y-1/2"
        :aria-expanded="open"
        aria-controls="period-picker"
        type="button"
        @click="toggle"
      >
        {{ selected }}
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
