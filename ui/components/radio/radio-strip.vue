<template>
  <div class="relative flex h-12 gap-0.5" data-testid="radio-strip">
    <button
      v-for="part in parts"
      :key="part.start"
      type="button"
      class="min-w-0 basis-0 cursor-pointer rounded-control border transition-colors hover:border-brass focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brass-deep"
      :class="[part.removed ? HATCH : 'bg-surface', now(part) ? 'border-ink' : 'border-hairline']"
      :style="{ flexGrow: part.end - part.start }"
      :title="part.label"
      :aria-label="part.label"
      :aria-current="now(part) ? 'true' : undefined"
      @click="emit('play', part.start)"
    />
    <div
      class="pointer-events-none absolute -inset-y-1 w-0.5 -translate-x-1/2 rounded-full bg-ink"
      :style="{ left: share(at) }"
    />
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import { HATCH, type StripPart } from '../../utils/radio'

const props = defineProps<{ parts: StripPart[]; at: number }>()

const emit = defineEmits<{ play: [start: number] }>()

const total = computed(() => props.parts[props.parts.length - 1]?.end ?? 0)

const share = (at: number) => `${total.value ? Math.min(100, (at / total.value) * 100) : 0}%`

const now = (part: StripPart) => part.start <= props.at && props.at < part.end
</script>
