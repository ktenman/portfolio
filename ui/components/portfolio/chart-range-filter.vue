<template>
  <div ref="root" class="relative md-down:w-full">
    <div class="flex" role="group" aria-label="Chart period">
      <button
        v-for="range in SHORTLIST"
        :key="range"
        :class="[PERIOD_CELL, ROW_CELL]"
        :data-active="pressed(range) || undefined"
        :aria-pressed="pressed(range)"
        type="button"
        @click="pick(range)"
      >
        {{ range }}
      </button>
      <button
        ref="more"
        :class="[PERIOD_CELL, ROW_CELL, 'inline-flex items-center justify-center gap-1']"
        :data-active="!!zoomLabel || !listed || undefined"
        :aria-expanded="open"
        aria-controls="period-picker"
        type="button"
        @click="toggle"
      >
        {{ moreLabel }}
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
      class="md-down:left-0"
      :selected="selected"
      @change="emit('select', $event)"
      @pick="pick"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, useTemplateRef } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { PERIOD_CELL, SHORTLIST, usePeriodPopover } from '../../composables/use-time-range'
import type { TimeRange } from '../../models/generated/domain-models'
import PeriodPopover from './period-popover.vue'

const ROW_CELL = 'md-down:min-w-fit md-down:flex-1 md-down:px-1'

const props = defineProps<{
  selected: TimeRange
  zoomLabel?: string | null
}>()

const emit = defineEmits<{
  select: [range: TimeRange]
}>()

const root = useTemplateRef('root')
const moreButton = useTemplateRef('more')
const { open, close, toggle } = usePeriodPopover(root, moreButton)

const isCompact = useMediaQuery('(max-width: 768px)')

const listed = computed(() => SHORTLIST.includes(props.selected))

const moreLabel = computed(() => {
  if (props.zoomLabel) return isCompact.value ? 'Custom' : props.zoomLabel
  return listed.value ? 'More' : props.selected
})

const pressed = (range: TimeRange) => !props.zoomLabel && range === props.selected

const pick = (range: TimeRange) => {
  emit('select', range)
  close()
}
</script>
