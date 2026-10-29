<template>
  <div ref="root" class="relative md-down:w-full">
    <div class="flex" role="group" aria-label="Chart period">
      <button
        v-for="range in SHORTLIST"
        :key="range"
        :class="[CELL, ROW_CELL]"
        :data-active="range === selected || undefined"
        :aria-pressed="range === selected"
        type="button"
        @click="pick(range)"
      >
        {{ range }}
      </button>
      <button
        ref="more"
        :class="[CELL, ROW_CELL, 'inline-flex items-center justify-center gap-1']"
        :data-active="!listed || undefined"
        :aria-expanded="open"
        aria-controls="period-picker"
        type="button"
        @click="toggle"
      >
        {{ listed ? 'More' : selected }}
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
    <div
      v-if="open"
      id="period-picker"
      class="absolute top-full right-0 z-(--z-dropdown) mt-1.5 grid gap-1.5 rounded-container border border-hairline bg-surface p-1.5 shadow-overlay md-down:left-0"
    >
      <div class="flex" role="group" aria-label="Unit">
        <button
          v-for="option in RANGE_UNITS"
          :key="option.value"
          :class="[CELL, 'flex-1']"
          :data-active="option.value === unit || undefined"
          :aria-pressed="option.value === unit"
          type="button"
          @click="pickUnit(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
      <div class="flex" role="group" aria-label="Length">
        <button
          v-for="length in RANGE_LENGTHS"
          :key="length"
          :class="[CELL, 'flex-1']"
          :data-active="length === period?.length || undefined"
          :aria-pressed="length === period?.length"
          :disabled="unit === null"
          type="button"
          @click="pickLength(length)"
        >
          {{ length }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useTemplateRef } from 'vue'
import { onClickOutside, onKeyStroke } from '@vueuse/core'
import {
  joinRange,
  RANGE_LENGTHS,
  RANGE_UNITS,
  type RangeUnit,
  SHORTLIST,
  splitRange,
} from '../../composables/use-time-range'
import type { TimeRange } from '../../models/generated/domain-models'

const CELL =
  'relative cursor-pointer border border-hairline bg-surface-sunken px-2.75 py-1.25 text-label font-medium whitespace-nowrap text-ink-soft transition-colors not-first:-ml-px first:rounded-l-container last:rounded-r-container focus-visible:z-2 enabled:hover:not-data-active:bg-surface-hover enabled:hover:not-data-active:text-ink disabled:cursor-not-allowed disabled:text-ink-faint data-active:z-1 data-active:border-brass data-active:bg-brass-wash data-active:text-brass-deep md-down:h-11'

const ROW_CELL = 'md-down:min-w-fit md-down:flex-1 md-down:px-1'

const props = defineProps<{
  selected: TimeRange
}>()

const emit = defineEmits<{
  select: [range: TimeRange]
}>()

const root = useTemplateRef('root')
const moreButton = useTemplateRef('more')
const open = ref(false)
const picked = ref<RangeUnit | null>(null)

const listed = computed(() => SHORTLIST.includes(props.selected))
const period = computed(() => splitRange(props.selected))
const unit = computed(() => period.value?.unit ?? picked.value)

const close = () => {
  open.value = false
}

const toggle = () => {
  picked.value = null
  open.value = !open.value
}

const pick = (range: TimeRange) => {
  emit('select', range)
  close()
}

const pickUnit = (value: RangeUnit) => {
  if (!period.value) {
    picked.value = value
    return
  }
  emit('select', joinRange(value, period.value.length))
}

const pickLength = (length: number) => {
  if (unit.value) pick(joinRange(unit.value, length))
}

onClickOutside(root, close)

onKeyStroke('Escape', () => {
  if (!open.value) return
  close()
  moreButton.value?.focus()
})
</script>
