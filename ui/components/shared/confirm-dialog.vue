<template>
  <modal-shell
    :open="modelValue"
    :modal-id="modalId"
    :title="title"
    :close-on-esc="false"
    @update:open="onDialogClosed"
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
        :class="{
          primary: confirmClass === 'btn-primary',
          danger: confirmClass === 'btn-danger',
        }"
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
  confirmClass?: string
}

const props = withDefaults(defineProps<Props>(), {
  modalId: 'confirmModal',
  title: 'Confirm',
  message: 'Are you sure?',
  confirmText: 'Confirm',
  cancelText: 'Cancel',
  confirmClass: 'btn-primary',
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

const onDialogClosed = () => {
  if (!props.modelValue) return
  cancel()
}
</script>
