<template>
  <div
    class="modal fade"
    :class="{ show: isOpen }"
    :style="{ display: isOpen ? 'block' : 'none' }"
    :id="modalId"
    tabindex="-1"
    :aria-labelledby="`${modalId}Label`"
    :aria-hidden="!isOpen"
    @click.self="cancel"
  >
    <div class="modal-dialog">
      <div class="modal-content">
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
          <button type="button" class="btn btn-secondary" @click="cancel">
            {{ cancelText }}
          </button>
          <button type="button" class="btn" :class="confirmClass" @click="confirm">
            {{ confirmText }}
          </button>
        </div>
      </div>
    </div>
  </div>
  <div v-if="isOpen" class="modal-backdrop fade" :class="{ show: isOpen }"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
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

const isOpen = ref(props.modelValue)
let modalInstance: Modal | null = null

onMounted(() => {
  const modalElement = document.getElementById(props.modalId)
  if (modalElement) {
    modalInstance = new Modal(modalElement, {
      backdrop: 'static',
      keyboard: false,
    })

    modalElement.addEventListener('hidden.bs.modal', () => {
      close()
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
    isOpen.value = newValue
    if (newValue && modalInstance) {
      modalInstance.show()
    } else if (!newValue && modalInstance) {
      modalInstance.hide()
    }
  }
)

const confirm = () => {
  emit('confirm')
  close()
}

const cancel = () => {
  emit('cancel')
  close()
}

const close = () => {
  isOpen.value = false
  emit('update:modelValue', false)
  if (modalInstance) {
    modalInstance.hide()
  }
}
</script>
