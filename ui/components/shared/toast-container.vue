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
