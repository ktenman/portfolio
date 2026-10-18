<template>
  <svg
    class="inline-block size-4 shrink-0 align-[-0.125em]"
    viewBox="0 0 24 24"
    :fill="SOLID.has(name) ? 'currentColor' : 'none'"
    stroke="currentColor"
    :stroke-width="SOLID.has(name) ? 0 : 2"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="path in PATHS[name]" :key="path" :d="path" />
  </svg>
</template>

<script lang="ts" setup>
export type IconName = 'back' | 'forward' | 'play' | 'pause' | 'left' | 'download' | 'check'

defineProps<{ name: IconName }>()

const SOLID = new Set<IconName>(['play', 'pause'])

const PATHS: Record<IconName, string[]> = {
  back: ['M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8', 'M3 3v5h5'],
  forward: ['M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8', 'M21 3v5h-5'],
  play: ['M7 4v16l13-8z'],
  pause: ['M6 4h4v16H6z', 'M14 4h4v16h-4z'],
  left: ['m15 18-6-6 6-6'],
  download: ['M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4', 'm7 10 5 5 5-5', 'M12 15V3'],
  check: ['M20 6 9 17l-5-5'],
}
</script>
