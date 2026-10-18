<template>
  <div>
    <div
      class="relative h-4 rounded-full has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-4 has-[:focus-visible]:outline-brass"
    >
      <div class="absolute inset-x-0 top-1.5 flex h-1 gap-0.5">
        <div
          v-for="part in parts"
          :key="part.start"
          class="h-full rounded-full"
          :class="part.cut ? 'bg-hairline' : 'bg-hairline-strong'"
          :style="{ flexGrow: part.end - part.start }"
        />
      </div>
      <div
        class="pointer-events-none absolute top-1.5 left-0 h-1 rounded-full bg-ink"
        :style="{ width: `${share}%` }"
      />
      <div
        class="pointer-events-none absolute top-0 size-4 -translate-x-1/2 rounded-full border-2 border-surface bg-ink shadow-control"
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
      />
    </div>
    <div v-if="ticks?.length" class="relative mt-2 h-4 text-label text-ink-soft">
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
import { position } from '../../utils/radio'

export interface Tick {
  at: number
  text: string
  phone?: boolean
}

const props = defineProps<{
  value: number
  total: number
  label: string
  parts?: { start: number; end: number; cut: boolean }[]
  ticks?: Tick[]
}>()

const emit = defineEmits<{ seek: [seconds: number] }>()

const parts = computed(() => props.parts ?? [{ start: 0, end: props.total, cut: false }])
const share = computed(() => (props.total ? Math.min(100, (props.value / props.total) * 100) : 0))

const place = (at: number) => {
  if (at <= 0) return ''
  return at >= props.total ? '-translate-x-full' : '-translate-x-1/2'
}
</script>
