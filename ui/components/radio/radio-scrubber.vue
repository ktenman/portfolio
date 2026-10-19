<template>
  <div
    class="relative rounded-full py-3.5 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-brass md:py-0"
  >
    <div class="relative h-4">
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
    </div>
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
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { position } from '../../utils/radio'

const props = defineProps<{
  value: number
  total: number
  label: string
  parts?: { start: number; end: number; cut: boolean }[]
}>()

const emit = defineEmits<{ seek: [seconds: number] }>()

const parts = computed(() => props.parts ?? [{ start: 0, end: props.total, cut: false }])
const share = computed(() => (props.total ? Math.min(100, (props.value / props.total) * 100) : 0))
</script>
