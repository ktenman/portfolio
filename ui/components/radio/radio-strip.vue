<template>
  <div data-testid="radio-strip">
    <div class="relative h-30 overflow-x-clip" aria-hidden="true">
      <span
        v-for="part in named"
        :key="part.start"
        data-testid="radio-strip-label"
        class="absolute bottom-1 max-w-40 origin-bottom-left -rotate-45 truncate text-2xs leading-none whitespace-nowrap"
        :class="tone(part)"
        :style="{ left: `calc(${share(part.start)} + 0.625rem)` }"
      >
        {{ part.name }}
      </span>
    </div>
    <div class="relative flex h-10 gap-0.5">
      <button
        v-for="part in parts"
        :key="part.start"
        type="button"
        class="@container relative min-w-0 basis-0 cursor-pointer overflow-hidden rounded-control border transition-colors hover:border-brass focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brass-deep"
        :class="[background(part), now(part) ? 'border-brass' : 'border-hairline']"
        :style="{ flexGrow: part.end - part.start }"
        :title="part.label"
        :aria-label="part.label"
        :aria-current="now(part) ? 'true' : undefined"
        @click="emit('play', part.start)"
      >
        <span class="absolute top-1.5 left-2 text-label text-ink-soft @max-[2.75rem]:hidden">
          {{ part.time }}
        </span>
        <span
          data-testid="radio-strip-played"
          class="absolute bottom-0 left-0 h-1 bg-ink"
          :style="{ width: `${played(part)}%` }"
        />
      </button>
      <div
        class="pointer-events-none absolute -inset-y-1.5 w-0.75 -translate-x-1/2 rounded-full bg-ink"
        :style="{ left: share(at) }"
      />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { HATCH, type StripPart } from '../../utils/radio'

const props = defineProps<{ parts: StripPart[]; at: number }>()

const emit = defineEmits<{ play: [start: number] }>()

const total = computed(() => props.parts[props.parts.length - 1]?.end ?? 0)

const named = computed(() => props.parts.filter(part => part.name !== null))

const share = (at: number) => `${total.value ? Math.min(100, (at / total.value) * 100) : 0}%`

const now = (part: StripPart) => part.start <= props.at && props.at < part.end

const played = (part: StripPart) =>
  Math.round(Math.max(0, Math.min(1, (props.at - part.start) / (part.end - part.start))) * 100)

const background = (part: StripPart) => {
  if (part.removed) return HATCH
  return now(part) ? 'bg-brass-wash' : 'bg-surface'
}

const tone = (part: StripPart) => {
  if (now(part)) return 'font-semibold text-brass-deep'
  return props.at >= part.end ? 'text-ink-soft' : 'text-ink'
}
</script>
