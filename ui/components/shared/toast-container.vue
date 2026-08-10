<template>
  <teleport to="body">
    <div class="toast-container position-fixed top-0 end-0 p-3" style="z-index: 9999">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="toast show align-items-center text-white border-0"
        :class="TOAST_STYLES[toast.type].bg"
        role="alert"
        aria-live="assertive"
        aria-atomic="true"
      >
        <div class="d-flex">
          <div class="toast-body">
            <strong>{{ TOAST_STYLES[toast.type].label }}</strong>
            {{ toast.message }}
          </div>
          <button
            type="button"
            class="btn-close btn-close-white me-2 m-auto"
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

const TOAST_STYLES: Record<ToastType, { bg: string; label: string }> = {
  success: { bg: 'bg-success', label: '✓ Success:' },
  error: { bg: 'bg-danger', label: '✕ Error:' },
  info: { bg: 'bg-info', label: 'ℹ Info:' },
  warning: { bg: 'bg-warning', label: '⚠ Warning:' },
}
</script>
