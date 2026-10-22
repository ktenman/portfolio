<template>
  <radio-card v-if="topics.length" title="Teemad" fold="open" data-testid="radio-topics">
    <template #aside="{ open }">
      <span v-if="!open && current" class="text-sm text-ink-soft tabular-nums">
        {{ current.time }}
      </span>
    </template>
    <button
      v-for="topic in topics"
      :key="topic.start"
      type="button"
      data-testid="radio-topics-row"
      class="grid w-full cursor-pointer grid-cols-[2.75rem_minmax(0,1fr)_auto] items-baseline gap-2.5 rounded-container px-2 py-2.5 text-left transition-colors focus-visible:outline-2 focus-visible:outline-brass-deep"
      :class="topic === current ? 'bg-brass-wash' : 'hover:bg-surface-hover'"
      :aria-current="topic === current ? 'true' : undefined"
      @click="emit('play', topic.start)"
    >
      <span
        class="text-label tabular-nums"
        :class="topic === current ? 'text-brass-deep' : 'text-ink-soft'"
      >
        {{ topic.time }}
      </span>
      <span :class="{ 'font-semibold text-brass-deep': topic === current }">
        {{ topic.title }}
      </span>
      <span class="text-label text-ink-soft">{{ minutes(span(topic)) }}</span>
    </button>
  </radio-card>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { minutes, topicAt, type StripPart } from '../../utils/radio'
import RadioCard from './radio-card.vue'

const props = defineProps<{ parts: StripPart[]; at: number }>()

const emit = defineEmits<{ play: [start: number] }>()

const topics = computed(() => props.parts.filter(part => part.name !== null))

const current = computed(() => {
  const here = props.parts.find(part => part.start <= props.at && props.at < part.end)
  const last = topics.value[topicAt(topics.value, props.at)]
  return here && last?.title === here.title ? last : undefined
})

const span = (topic: StripPart) => {
  const next = topics.value.find(other => other.start > topic.start)?.start ?? Infinity
  return props.parts
    .filter(part => part.title === topic.title && part.start >= topic.start && part.start < next)
    .reduce((sum, part) => sum + part.end - part.start, 0)
}
</script>
