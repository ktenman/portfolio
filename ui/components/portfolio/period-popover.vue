<template>
  <div
    id="period-picker"
    class="absolute top-full right-0 z-(--z-dropdown) mt-1.5 grid gap-1.5 rounded-container border border-hairline bg-surface p-1.5 shadow-overlay"
  >
    <slot />
    <div class="flex" role="group" aria-label="Unit">
      <button
        v-for="option in RANGE_UNITS"
        :key="option.value"
        :class="[PERIOD_CELL, 'flex-1']"
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
        :class="[PERIOD_CELL, 'flex-1']"
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
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  joinRange,
  PERIOD_CELL,
  RANGE_LENGTHS,
  RANGE_UNITS,
  type RangeUnit,
  splitRange,
} from '../../composables/use-time-range'
import type { TimeRange } from '../../models/generated/domain-models'

const props = defineProps<{
  selected: TimeRange
}>()

const emit = defineEmits<{
  change: [range: TimeRange]
  pick: [range: TimeRange]
}>()

const picked = ref<RangeUnit | null>(null)

const period = computed(() => splitRange(props.selected))
const unit = computed(() => period.value?.unit ?? picked.value)

const pickUnit = (value: RangeUnit) => {
  if (!period.value) {
    picked.value = value
    return
  }
  emit('change', joinRange(value, period.value.length))
}

const pickLength = (length: number) => {
  if (unit.value) emit('pick', joinRange(unit.value, length))
}
</script>
