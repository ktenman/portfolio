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
export type IconName =
  | 'back'
  | 'forward'
  | 'play'
  | 'pause'
  | 'left'
  | 'down'
  | 'download'
  | 'check'
  | 'volume'
  | 'muted'
  | 'alert'
  | 'trash'

defineProps<{ name: IconName }>()

const SOLID = new Set<IconName>(['play', 'pause'])

const PATHS: Record<IconName, string[]> = {
  back: ['M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8', 'M3 3v5h5'],
  forward: ['M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8', 'M21 3v5h-5'],
  play: ['M7 4v16l13-8z'],
  pause: ['M6 4h4v16H6z', 'M14 4h4v16h-4z'],
  left: ['m15 18-6-6 6-6'],
  down: ['m6 9 6 6 6-6'],
  download: ['M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4', 'm7 10 5 5 5-5', 'M12 15V3'],
  check: ['M20 6 9 17l-5-5'],
  alert: ['M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0', 'M12 8v4', 'M12 16h.01'],
  trash: [
    'M3 6h18',
    'M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6',
    'M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2',
  ],
  volume: [
    'M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z',
    'M16 9a5 5 0 0 1 0 6',
    'M19.364 18.364a9 9 0 0 0 0-12.728',
  ],
  muted: [
    'M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z',
    'm22 9-6 6',
    'm16 9 6 6',
  ],
}
</script>
