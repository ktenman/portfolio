<template>
  <div
    v-if="current"
    data-testid="radio-now"
    class="grid grid-cols-[2.75rem_1fr_2.75rem] items-center gap-1 rounded-container bg-brass-wash px-1 py-2.5 md:hidden"
  >
    <button
      v-if="previous"
      type="button"
      aria-label="Eelmine teema"
      :class="STEP"
      @click="emit('play', previous.start)"
    >
      <radio-icon name="left" />
    </button>
    <span v-else />
    <div class="grid gap-0.5 text-center">
      <p class="mb-0 text-label text-brass-deep tabular-nums">{{ current.time }}</p>
      <p class="mb-0 font-semibold">{{ current.title }}</p>
      <p v-if="current.summary" data-testid="radio-now-summary" class="mb-0 text-2xs text-ink-soft">
        {{ current.summary }}
      </p>
    </div>
    <button
      v-if="next"
      type="button"
      aria-label="Järgmine teema"
      :class="STEP"
      @click="emit('play', next.start)"
    >
      <radio-icon name="right" />
    </button>
    <span v-else />
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { topicAt, type StripPart } from '../../utils/radio'
import RadioIcon from './radio-icon.vue'

const props = defineProps<{ parts: StripPart[]; at: number }>()

const emit = defineEmits<{ play: [start: number] }>()

const STEP =
  'inline-flex size-11 cursor-pointer items-center justify-center rounded-full text-brass-deep transition-colors hover:bg-surface focus-visible:outline-2 focus-visible:outline-brass-deep'

const topics = computed(() => props.parts.filter(part => part.name !== null))

const index = computed(() => topicAt(topics.value, props.at))

const current = computed(() => (topics.value.length > 1 ? topics.value[index.value] : undefined))

const previous = computed(() => topics.value[index.value - 1])

const next = computed(() => topics.value[index.value + 1])
</script>
