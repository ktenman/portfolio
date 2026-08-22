<template>
  <teleport to="body">
    <div class="toast-container">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="toast show"
        :class="TOAST_STYLES[toast.type].accent"
        role="alert"
        aria-live="assertive"
        aria-atomic="true"
      >
        <div class="flex items-center">
          <div class="toast-body">
            <strong>{{ TOAST_STYLES[toast.type].label }}</strong>
            {{ toast.message }}
          </div>
          <button
            type="button"
            class="btn-close me-2 m-auto"
            aria-label="Close"
            @click="dismissToast(toast.id)"
          ></button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { dismissToast, toasts, type ToastType } from '../../composables/use-toast'

const TOAST_STYLES: Record<ToastType, { accent: string; label: string }> = {
  success: { accent: 'toast-success', label: '✓ Success:' },
  error: { accent: 'toast-error', label: '✕ Error:' },
  info: { accent: 'toast-info', label: 'ℹ Info:' },
  warning: { accent: 'toast-warning', label: '⚠ Warning:' },
}
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 0;
  right: 0;
  z-index: var(--z-toast);
  width: max-content;
  max-width: 100%;
  padding: 1rem;
  pointer-events: none;
}

.toast {
  --toast-bg: var(--color-control-graphite);
  width: 350px;
  max-width: 100%;
  font-size: var(--text-sm);
  pointer-events: auto;
  color: var(--color-white);
  background-color: var(--toast-bg);
  background-clip: padding-box;
  border-radius: var(--radius-control);
  box-shadow: var(--shadow-overlay);
}

.toast .btn-close {
  color: var(--color-white);
}

.toast-container > :not(:last-child) {
  margin-bottom: 1.5rem;
}

.toast-success {
  --toast-bg: var(--color-status-success);
}

.toast-info {
  --toast-bg: var(--color-status-info);
}

.toast-error {
  --toast-bg: var(--color-status-danger);
}

.toast-warning {
  --toast-bg: var(--color-status-warning);
}

.toast-body {
  padding: 0.75rem;
  word-wrap: break-word;
}
</style>
