<template>
  <modal-shell
    :open="modelValue"
    :modal-id="modalId"
    :title="title"
    :close-on-esc="false"
    @update:open="cancel"
  >
    <p>{{ message }}</p>
    <template #footer>
      <button
        type="button"
        class="dialog-btn"
        @click="cancel"
        data-testid="confirmDialogCancelButton"
      >
        {{ cancelText }}
      </button>
      <button
        type="button"
        class="dialog-btn"
        :class="variant"
        @click="confirm"
        data-testid="confirmDialogConfirmButton"
      >
        {{ confirmText }}
      </button>
    </template>
  </modal-shell>
</template>

<script setup lang="ts">
import ModalShell from './modal-shell.vue'

interface Props {
  modelValue: boolean
  modalId?: string
  title?: string
  message?: string
  confirmText?: string
  cancelText?: string
  variant?: 'primary' | 'danger'
}

withDefaults(defineProps<Props>(), {
  modalId: 'confirmModal',
  title: 'Confirm',
  message: 'Are you sure?',
  confirmText: 'Confirm',
  cancelText: 'Cancel',
  variant: 'primary',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  cancel: []
}>()

const confirm = () => {
  emit('confirm')
  emit('update:modelValue', false)
}

const cancel = () => {
  emit('cancel')
  emit('update:modelValue', false)
}
</script>
