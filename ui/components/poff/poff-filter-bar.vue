<template>
  <poff-day-strip v-model="day" :days="days" :today="today" :clearable="clearable" />
  <div ref="chips">
    <poff-filter-chips :films="films" :switches="switches" />
  </div>
  <slot />
  <div
    v-if="pinned"
    class="fixed inset-x-0 top-0 z-20 border-b border-hairline bg-surface not-md-down:hidden"
  >
    <button
      type="button"
      class="flex h-12 w-full cursor-pointer items-center gap-3 px-3 text-left"
      :aria-expanded="open"
      aria-controls="poff-pinned-panel"
      data-testid="poff-pinned"
      @click="open = !open"
    >
      <radio-icon name="filter" class="size-5 text-ink-soft" />
      <span class="min-w-0 flex-1 truncate text-ink-soft">
        <strong class="font-semibold text-ink">{{ summary[0] }}</strong>
        <template v-for="part in summary.slice(1)" :key="part">{{ ' · ' + part }}</template>
      </span>
      <radio-icon name="down" class="size-5" :class="{ 'rotate-180': open }" />
    </button>
    <div v-if="open" class="max-h-[70vh] overflow-y-auto px-3 pt-1" id="poff-pinned-panel">
      <poff-search-field class="mb-3" />
      <poff-day-strip v-model="day" :days="days" :today="today" :clearable="clearable" />
      <poff-filter-chips :films="films" :switches="switches" />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, useTemplateRef, watch } from 'vue'
import { useIntersectionObserver } from '@vueuse/core'
import type { PoffFilm } from '../../models/generated/domain-models'
import type { FILTER_LABELS } from '../../utils/poff'
import RadioIcon from '../radio/radio-icon.vue'
import PoffDayStrip from './poff-day-strip.vue'
import PoffFilterChips from './poff-filter-chips.vue'
import PoffSearchField from './poff-search-field.vue'

defineProps<{
  films: PoffFilm[]
  days: string[]
  today: string
  clearable?: boolean
  switches: (keyof typeof FILTER_LABELS)[]
  summary: string[]
}>()

const day = defineModel<string | null>('day', { required: true })

const pinned = ref(false)
const open = ref(false)
useIntersectionObserver(useTemplateRef<HTMLElement>('chips'), ([entry]) => {
  pinned.value = !entry!.isIntersecting && entry!.boundingClientRect.bottom < 0
})
watch(pinned, on => {
  if (!on) open.value = false
})
</script>
