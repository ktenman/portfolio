<template>
  <div v-if="current" data-testid="radio-now" class="rounded-container bg-brass-wash">
    <div class="flex items-start justify-between gap-6 px-4 py-3.5 max-md:hidden">
      <div class="grid gap-1">
        <p class="mb-0 font-semibold">
          <span class="text-brass-deep tabular-nums">{{ number }}/{{ topics.length }}</span>
          {{ current.title }}
        </p>
        <p
          v-if="current.summary"
          data-testid="radio-now-summary"
          class="mb-0 text-sm text-ink-soft"
        >
          {{ current.summary }}
        </p>
      </div>
      <div class="flex shrink-0 items-center gap-3">
        <span class="text-2xs text-ink-soft tabular-nums">
          {{ position(current.start) }}–{{ position(current.end) }} · {{ minutes }} min
        </span>
        <app-button v-if="next" size="sm" @click="emit('play', next.start)">
          Järgmine teema →
        </app-button>
      </div>
    </div>
    <div class="grid grid-cols-[2.75rem_1fr_2.75rem] items-center gap-1 px-1 py-2.5 md:hidden">
      <button
        v-if="previous"
        type="button"
        aria-label="Eelmine teema"
        :class="STEP"
        @click="emit('play', previous.start)"
      >
        ‹
      </button>
      <span v-else />
      <div class="grid gap-0.5 text-center">
        <p class="mb-0 font-semibold">{{ current.title }}</p>
        <p v-if="current.summary" class="mb-0 text-2xs text-ink-soft">{{ current.summary }}</p>
        <p class="mb-0 text-label text-brass-deep tabular-nums">
          {{ position(current.start) }} · {{ minutes }} min
        </p>
      </div>
      <button
        v-if="next"
        type="button"
        aria-label="Järgmine teema"
        :class="STEP"
        @click="emit('play', next.start)"
      >
        ›
      </button>
      <span v-else />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { position, topicAt, type StripPart } from '../../utils/radio'
import AppButton from '../shared/app-button.vue'

const props = defineProps<{ parts: StripPart[]; at: number }>()

const emit = defineEmits<{ play: [start: number] }>()

const STEP =
  'size-11 cursor-pointer rounded-full text-xl text-brass-deep transition-colors hover:bg-surface focus-visible:outline-2 focus-visible:outline-brass-deep'

const topics = computed(() => props.parts.filter(part => part.name !== null))

const index = computed(() => topicAt(topics.value, props.at))

const current = computed(() => (topics.value.length > 1 ? topics.value[index.value] : undefined))

const number = computed(() => index.value + 1)

const previous = computed(() => topics.value[index.value - 1])

const next = computed(() => topics.value[index.value + 1])

const minutes = computed(() =>
  current.value ? Math.max(1, Math.round((current.value.end - current.value.start) / 60)) : 0
)
</script>
