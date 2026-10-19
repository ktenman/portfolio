<template>
  <section
    data-testid="radio-section"
    :data-start="section.start"
    :aria-current="playing ? 'true' : undefined"
    class="scroll-mt-20 border-b border-hairline py-2 last:border-b-0"
  >
    <button
      type="button"
      data-testid="radio-section-heading"
      class="flex min-h-11 w-full cursor-pointer items-baseline gap-3 rounded-container px-2 py-2 text-left transition-colors hover:bg-surface-hover"
      @click="emit('play', section.start)"
    >
      <span class="w-14 shrink-0 text-sm text-ink-soft tabular-nums">
        {{ sectionTime(section, raw, startedAt) }}
      </span>
      <span class="min-w-0 grow font-semibold">{{ section.title }}</span>
      <radio-badge v-if="playing" tone="live">Praegu</radio-badge>
      <span class="text-sm text-ink-soft tabular-nums">
        {{ minutes(section.end - section.start) }}
      </span>
    </button>
    <ol class="mb-0 list-none p-0">
      <li
        v-for="line in section.lines"
        :key="line.start"
        v-memo="[line, line === current, line === current && spoken]"
        data-testid="radio-text-line"
        :aria-current="line === current ? 'true' : undefined"
        class="grid scroll-mt-20 scroll-mb-32 grid-cols-[4.5rem_minmax(0,1fr)] gap-3 rounded-container px-2 py-2.5"
        :class="{ 'bg-brass-wash': line === current }"
      >
        <button
          type="button"
          class="cursor-pointer self-start text-left text-sm font-semibold text-ink-soft tabular-nums hover:text-ink"
          @click="emit('play', line.start)"
        >
          {{ clock(startedAt, line.start, true) }}
        </button>
        <div>
          <p class="mb-0" :class="{ 'text-ink-soft': line.music }">
            <template v-if="line === current && line.words.length">
              <template v-for="word in line.words" :key="word.start">
                <span
                  :data-testid="word === spoken ? 'radio-word-now' : undefined"
                  :class="{ 'rounded-[2px] bg-brass/20 box-decoration-clone': word === spoken }"
                >
                  {{ word.word.trim() }}
                </span>
                {{ ' ' }}
              </template>
            </template>
            <template v-else>{{ line.music ? 'Muusika' : line.text.trim() }}</template>
          </p>
          <p v-if="line.split" class="mt-1 mb-0 flex flex-wrap items-center gap-2 text-sm">
            <radio-badge tone="warn">Lõige poolitab lause</radio-badge>
            <span class="text-ink-soft">
              {{ line.split.title }}
              {{ range(startedAt, line.split.start, line.split.end) }}
            </span>
          </p>
        </div>
      </li>
    </ol>
  </section>
</template>

<script lang="ts" setup>
import type {
  TranscriptLine,
  TranscriptSection,
  TranscriptWord,
} from '../../models/generated/domain-models'
import { clock, minutes, range, sectionTime } from '../../utils/radio'
import RadioBadge from './radio-badge.vue'

defineProps<{
  section: TranscriptSection
  startedAt: number
  raw: boolean
  playing: boolean
  current?: TranscriptLine
  spoken?: TranscriptWord
}>()

const emit = defineEmits<{ play: [at: number] }>()
</script>
