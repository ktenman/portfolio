<template>
  <div>
    <div
      v-if="named.length && !bare"
      data-testid="radio-timeline-labels"
      class="mb-2 flex gap-0.5 max-md:hidden"
    >
      <div
        v-for="part in parts"
        :key="part.start"
        class="@container min-w-0 basis-0"
        :style="{ flexGrow: part.end - part.start }"
      >
        <button
          v-if="part.name"
          type="button"
          data-testid="radio-timeline-label"
          class="grid max-w-full cursor-pointer pr-2 text-left focus-visible:outline-2 focus-visible:outline-brass-deep @max-[2.5rem]:hidden"
          :title="part.summary ?? undefined"
          :aria-label="`${part.time} ${part.name}`"
          :aria-current="now(part) ? 'true' : undefined"
          @click="emit('play', part.start)"
        >
          <span class="text-label text-ink-soft tabular-nums">{{ part.time }}</span>
          <span class="truncate text-sm @max-[6rem]:hidden" :class="tone(part)">
            {{ part.name }}
          </span>
          <span
            v-if="programme && part === named[0]"
            class="hidden truncate text-sm @max-[6rem]:block"
            :class="tone(part)"
          >
            {{ programme }}
          </span>
        </button>
      </div>
    </div>
    <div
      class="relative h-7 rounded-full has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-4 has-[:focus-visible]:outline-brass"
    >
      <div class="absolute inset-x-0 top-2 flex h-3 gap-0.5 md:top-2.5 md:h-2">
        <div
          v-for="part in parts"
          :key="part.start"
          data-testid="radio-timeline-part"
          class="relative min-w-0 basis-0 overflow-hidden rounded-sm"
          :class="part.removed ? HATCH : 'bg-hairline-strong'"
          :style="{ flexGrow: part.end - part.start }"
        >
          <div
            class="h-full"
            :class="part.removed ? 'bg-ink/40' : 'bg-ink'"
            :style="{ width: `${played(part)}%` }"
          />
        </div>
      </div>
      <div
        class="pointer-events-none absolute top-0 h-7 w-1 -translate-x-1/2 rounded-full bg-brass ring-2 ring-surface"
        :style="{ left: `${share}%` }"
      />
      <input
        type="range"
        class="absolute inset-0 w-full cursor-pointer opacity-0 max-md:-inset-y-2"
        min="0"
        :max="total"
        step="1"
        :value="value"
        :aria-label="label"
        :aria-valuetext="`${position(value)} / ${position(total)}`"
        @input="emit('seek', Number(($event.target as HTMLInputElement).value))"
        @click="tap"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { HATCH, position, type StripPart } from '../../utils/radio'

const props = defineProps<{
  value: number
  total: number
  label: string
  parts: StripPart[]
  programme?: string
  bare?: boolean
}>()

const emit = defineEmits<{ seek: [seconds: number]; play: [start: number] }>()

const named = computed(() => props.parts.filter(part => part.name !== null))

const share = computed(() => (props.total ? Math.min(100, (props.value / props.total) * 100) : 0))

const here = computed(() =>
  props.parts.find(part => part.start <= props.value && props.value < part.end)
)

const end = (part: StripPart) => {
  const next = named.value.find(other => other.start > part.start)?.start ?? Infinity
  const stretches = props.parts.filter(
    other => other.title === part.title && other.start >= part.start && other.start < next
  )
  return Math.max(...stretches.map(stretch => stretch.end))
}

const now = (part: StripPart) =>
  here.value?.title === part.title && part.start <= props.value && props.value < end(part)

const played = (part: StripPart) =>
  Math.min(100, Math.max(0, ((props.value - part.start) / (part.end - part.start)) * 100))

const tone = (part: StripPart) => {
  if (now(part)) return 'font-semibold text-brass-deep'
  return props.value >= end(part) ? 'text-ink-soft' : 'text-ink'
}

const tap = (event: MouseEvent) => {
  const box = (event.currentTarget as HTMLElement).getBoundingClientRect()
  if (!box.width) return
  emit('seek', Math.round(((event.clientX - box.left) / box.width) * props.total))
}
</script>
