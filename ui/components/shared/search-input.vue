<template>
  <div class="search-input-wrapper">
    <svg class="search-icon" viewBox="0 0 16 16" aria-hidden="true">
      <circle cx="7" cy="7" r="4.75" fill="none" stroke="currentColor" stroke-width="1.5" />
      <path d="M10.6 10.6 14 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
    </svg>
    <input
      ref="input"
      v-model="query"
      type="text"
      class="search-input"
      :placeholder="placeholder"
      :aria-label="label"
      :maxlength="maxlength"
    />
    <button
      v-if="query.trim()"
      class="search-clear-btn"
      @click="clear"
      type="button"
      aria-label="Clear search"
    >
      &times;
    </button>
  </div>
</template>

<script lang="ts" setup>
import { useTemplateRef } from 'vue'

defineProps<{ placeholder: string; label: string; maxlength?: number }>()
const query = defineModel<string>({ required: true })
const input = useTemplateRef<HTMLInputElement>('input')

const clear = () => {
  query.value = ''
  input.value?.focus()
}
</script>

<style scoped>
.search-input-wrapper {
  position: relative;
  flex: 1;
  max-width: 320px;
}

.search-icon {
  position: absolute;
  left: 0.6875rem;
  top: 50%;
  transform: translateY(-50%);
  width: 15px;
  height: 15px;
  color: var(--color-ink-faint);
  pointer-events: none;
  transition: color var(--transition-fast);
}

.search-input-wrapper:focus-within .search-icon {
  color: var(--color-brass);
}

.search-input {
  width: 100%;
  padding: 0.4375rem 2rem 0.4375rem 2.125rem;
  border: 1px solid var(--color-control-border);
  border-radius: var(--radius-container);
  font-size: var(--text-sm);
  color: var(--color-ink);
  background: var(--color-surface-subtle);
  transition: background-color var(--transition-fast);
}

.search-input:focus {
  background: var(--color-surface);
}

.search-input::placeholder {
  color: var(--color-ink-soft);
}

.search-clear-btn {
  position: absolute;
  right: 0.4375rem;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.25rem;
  height: 1.25rem;
  padding: 0;
  background: none;
  border: none;
  border-radius: var(--radius-control);
  color: var(--color-ink-soft);
  font-size: 1.125rem;
  line-height: 1;
  cursor: pointer;
}

.search-clear-btn:hover {
  background: var(--color-brass-wash);
  color: var(--color-ink);
}

@media (max-width: 768px) {
  .search-input-wrapper {
    width: 100%;
    max-width: none;
  }
}
</style>
