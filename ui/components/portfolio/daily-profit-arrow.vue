<template>
  <button
    type="button"
    class="arrow-btn size-8 md-down:size-11"
    :aria-label="label"
    :title="label"
    :aria-disabled="disabled ? 'true' : undefined"
    @click="press"
  >
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path :d="PATHS[direction]" />
    </svg>
  </button>
</template>

<script lang="ts" setup>
const PATHS = { back: 'M10 3.5 5.5 8l4.5 4.5', forward: 'M6 3.5 10.5 8 6 12.5' }

const props = defineProps<{
  label: string
  direction: 'back' | 'forward'
  disabled?: boolean
}>()

const emit = defineEmits<{ press: [] }>()

const press = () => {
  if (props.disabled) return
  emit('press')
}
</script>

<style scoped>
.arrow-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-ink);
  cursor: pointer;
  transition: all var(--transition-chip);
}

@media (hover: hover) {
  .arrow-btn:hover {
    background: var(--color-surface-hover);
    border-color: var(--color-hairline-strong);
  }
}

.arrow-btn:active {
  transform: scale(0.96);
}

.arrow-btn[aria-disabled='true'] {
  color: var(--color-ink-faint);
  background: transparent;
  border-color: transparent;
  cursor: default;
  transform: none;
}

.arrow-btn svg {
  width: 1rem;
  height: 1rem;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
