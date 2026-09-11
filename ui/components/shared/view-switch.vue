<template>
  <div class="view-switch" role="group" aria-label="Chart style">
    <button
      class="view-btn"
      :class="{ active: modelValue === 'donut' }"
      :aria-pressed="modelValue === 'donut'"
      aria-label="Donut"
      title="Donut"
      type="button"
      @click="emit('update:modelValue', 'donut')"
    >
      <svg viewBox="0 0 16 16" aria-hidden="true">
        <circle
          cx="8"
          cy="8"
          r="5.4"
          fill="none"
          stroke="currentColor"
          stroke-width="2.6"
          stroke-dasharray="8.6 2.71"
        />
      </svg>
    </button>
    <button
      class="view-btn"
      :class="{ active: modelValue === 'bars' }"
      :aria-pressed="modelValue === 'bars'"
      aria-label="Bars"
      title="Bars"
      type="button"
      @click="emit('update:modelValue', 'bars')"
    >
      <svg viewBox="0 0 16 16" aria-hidden="true">
        <rect x="2" y="3.2" width="12" height="2.2" rx="1.1" fill="currentColor" />
        <rect x="2" y="6.9" width="8" height="2.2" rx="1.1" fill="currentColor" />
        <rect x="2" y="10.6" width="4.5" height="2.2" rx="1.1" fill="currentColor" />
      </svg>
    </button>
  </div>
</template>

<script lang="ts" setup>
export type BreakdownView = 'donut' | 'bars'

defineProps<{
  modelValue: BreakdownView
}>()

const emit = defineEmits<{
  'update:modelValue': [view: BreakdownView]
}>()
</script>

<style scoped>
.view-switch {
  display: inline-flex;
  margin-left: 0.5rem;
  border: 1px solid var(--color-control-border);
  border-radius: var(--radius-container);
  overflow: hidden;
}

.view-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.875rem;
  height: 1.625rem;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-ink-faint);
  cursor: pointer;
}

.view-btn + .view-btn {
  border-left: 1px solid var(--color-control-border);
}

.view-btn:hover {
  background: var(--color-surface-hover);
  color: var(--color-ink);
}

.view-btn.active {
  background: var(--color-brass-wash);
  color: var(--color-brass-deep);
}

.view-btn svg {
  width: 0.9375rem;
  height: 0.9375rem;
  display: block;
}
</style>
