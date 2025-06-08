<template>
  <div v-if="isOpen" class="modal fade show d-block" tabindex="-1" @click="handleBackdropClick">
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content">
        <div :class="headerClass" class="modal-header">
          <h5 class="modal-title">{{ options.title }}</h5>
          <button aria-label="Close" class="btn-close" type="button" @click="handleCancel"></button>
        </div>
        <div class="modal-body">
          <p class="mb-0">{{ options.message }}</p>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" type="button" @click="handleCancel">
            {{ options.cancelText }}
          </button>
          <button :class="confirmButtonClass" class="btn" type="button" @click="handleConfirm">
            {{ options.confirmText }}
          </button>
        </div>
      </div>
    </div>
  </div>
  <div v-if="isOpen" class="modal-backdrop fade show"></div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'
import type { ConfirmOptions } from '../../composables/use-confirm-dialog'

interface Props {
  isOpen: boolean
  options: ConfirmOptions
}

interface Emits {
  (e: 'confirm'): void

  (e: 'cancel'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const headerClass = computed(() => {
  const typeClasses = {
    danger: 'bg-danger text-white',
    warning: 'bg-warning',
    info: 'bg-info',
  }
  return typeClasses[props.options.type || 'info'] || ''
})

const confirmButtonClass = computed(() => {
  const typeClasses = {
    danger: 'btn-danger',
    warning: 'btn-warning',
    info: 'btn-primary',
  }
  return typeClasses[props.options.type || 'info'] || 'btn-primary'
})

const handleConfirm = () => {
  emit('confirm')
}

const handleCancel = () => {
  emit('cancel')
}

const handleBackdropClick = (event: MouseEvent) => {
  if ((event.target as HTMLElement).classList.contains('modal')) {
    handleCancel()
  }
}
</script>
