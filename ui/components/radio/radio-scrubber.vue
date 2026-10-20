<template>
  <div>
    <div
      class="relative h-4 rounded-full has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-4 has-[:focus-visible]:outline-brass max-md:h-7"
    >
      <div class="absolute inset-x-0 top-1.5 h-1 rounded-full bg-hairline-strong max-md:hidden" />
      <div
        class="pointer-events-none absolute top-1.5 left-0 h-1 rounded-full bg-ink max-md:hidden"
        :style="{ width: `${share}%` }"
      />
      <div
        class="pointer-events-none absolute top-0 size-4 -translate-x-1/2 rounded-full border-2 border-surface bg-ink shadow-control max-md:hidden"
        :style="{ left: `${share}%` }"
      />
      <div class="absolute inset-x-0 top-2 flex h-3 gap-0.5 md:hidden">
        <div
          v-for="part in parts"
          :key="part.start"
          data-testid="radio-timeline-part"
          class="relative overflow-hidden rounded-sm"
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
        class="pointer-events-none absolute top-0 h-7 w-1 -translate-x-1/2 rounded-full bg-brass ring-2 ring-surface md:hidden"
        :style="{ left: `${share}%` }"
      />
      <input
        type="range"
        class="absolute inset-0 w-full cursor-pointer opacity-0"
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
    <div v-if="ticks?.length" class="relative mt-2 h-4 text-label text-ink-soft">
      <span
        v-if="caption"
        data-testid="radio-timeline-caption"
        class="absolute left-1/2 -translate-x-1/2 whitespace-nowrap md:hidden"
      >
        {{ caption }}
      </span>
      <span
        v-for="tick in ticks"
        :key="tick.at"
        class="absolute whitespace-nowrap"
        :class="[tick.phone ? '' : 'hidden md:inline', place(tick.at)]"
        :style="{ left: `${(tick.at / total) * 100}%` }"
      >
        {{ tick.text }}
      </span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { HATCH, position, type StripPart } from '../../utils/radio'

export interface Tick {
  at: number
  text: string
  phone?: boolean
}

const props = defineProps<{
  value: number
  total: number
  label: string
  parts?: StripPart[]
  caption?: string
  ticks?: Tick[]
}>()

const emit = defineEmits<{ seek: [seconds: number] }>()

const at = (seconds: number) => (props.total ? Math.min(100, (seconds / props.total) * 100) : 0)
const share = computed(() => at(props.value))

const played = (part: StripPart) =>
  Math.min(100, Math.max(0, ((props.value - part.start) / (part.end - part.start)) * 100))

const tap = (event: MouseEvent) => {
  const box = (event.currentTarget as HTMLElement).getBoundingClientRect()
  if (!box.width) return
  emit('seek', Math.round(((event.clientX - box.left) / box.width) * props.total))
}

const place = (at: number) => {
  if (at <= 0) return ''
  return at >= props.total ? '-translate-x-full' : '-translate-x-1/2'
}
</script>
