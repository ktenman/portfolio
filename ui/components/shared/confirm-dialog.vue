<template>
  <div
    class="modal fade"
    :id="modalId"
    tabindex="-1"
    :aria-labelledby="`${modalId}Label`"
    aria-hidden="true"
    @click.self="cancel"
  >
    <div class="modal-dialog">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h5 class="modal-title" :id="`${modalId}Label`">
            {{ title }}
          </h5>
          <button type="button" class="btn-close" @click="cancel" aria-label="Close"></button>
        </div>
        <div class="modal-body">
          <p>{{ message }}</p>
        </div>
        <div class="modal-footer">
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
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { watch, onMounted, onUnmounted } from 'vue'
import { Modal } from 'bootstrap'

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

let modalInstance: Modal | null = null

onMounted(() => {
  const modalElement = document.getElementById(props.modalId)
  if (modalElement) {
    modalInstance = new Modal(modalElement, {
      backdrop: 'static',
      keyboard: false,
    })

    modalElement.addEventListener('hidden.bs.modal', () => {
      emit('update:modelValue', false)
    })
  }
})

onUnmounted(() => {
  if (modalInstance) {
    modalInstance.dispose()
  }
})

watch(
  () => props.modelValue,
  newValue => {
    if (modalInstance) {
      if (newValue) {
        modalInstance.show()
      } else {
        modalInstance.hide()
      }
    }
  }
)

const confirm = () => {
  emit('confirm')
  emit('update:modelValue', false)
}

const cancel = () => {
  emit('cancel')
  emit('update:modelValue', false)
}
</script>
