<template>
  <div
    v-if="chips"
    data-testid="radio-topic-chips"
    class="-mx-3 flex gap-2 overflow-x-auto px-3 pb-1 [scrollbar-width:none]"
  >
    <button
      v-for="section in sections"
      :key="section.start"
      type="button"
      data-testid="radio-topic"
      class="platform-btn min-h-11 shrink-0"
      :class="{ active: section === playing }"
      @click="emit('pick', section)"
    >
      <span class="text-ink-soft tabular-nums">{{ sectionTime(section, raw, startedAt) }}</span>
      {{ section.title }}
    </button>
  </div>
  <radio-card v-else title="Teemad" data-testid="radio-topics">
    <p v-if="!sections.length" class="mb-0 text-ink-soft">Teemasid veel pole.</p>
    <ul v-else class="mb-0 list-none p-0">
      <li
        v-for="section in sections"
        :key="section.start"
        class="border-b border-hairline last:border-b-0"
      >
        <button
          type="button"
          data-testid="radio-topic"
          :aria-current="section === playing ? 'true' : undefined"
          class="grid min-h-11 w-full cursor-pointer grid-cols-[3.5rem_minmax(0,1fr)_auto] items-baseline gap-3 px-1 py-2.5 text-left transition-colors hover:bg-surface-hover aria-[current=true]:font-semibold"
          @click="emit('pick', section)"
        >
          <span class="font-normal text-ink-soft tabular-nums">
            {{ sectionTime(section, raw, startedAt) }}
          </span>
          <span class="flex flex-wrap items-baseline gap-2">
            {{ section.title }}
            <radio-badge v-if="section.cut !== null" tone="plain">lõige</radio-badge>
          </span>
          <span class="text-sm font-normal text-ink-soft tabular-nums">
            {{ minutes(section.end - section.start) }}
          </span>
        </button>
      </li>
    </ul>
  </radio-card>
</template>

<script lang="ts" setup>
import type { TranscriptSection } from '../../models/generated/domain-models'
import { minutes, sectionTime } from '../../utils/radio'
import RadioBadge from './radio-badge.vue'
import RadioCard from './radio-card.vue'

defineProps<{
  sections: TranscriptSection[]
  startedAt: number
  raw: boolean
  playing?: TranscriptSection
  chips?: boolean
}>()

const emit = defineEmits<{ pick: [section: TranscriptSection] }>()
</script>
