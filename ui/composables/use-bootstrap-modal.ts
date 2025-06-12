import { onMounted, onUnmounted, ref } from 'vue'
import { Modal } from 'bootstrap'

export function useBootstrapModal(modalId: string) {
  const modalInstance = ref<Modal | null>(null)

  const show = () => {
    modalInstance.value?.show()
  }

  const hide = () => {
    modalInstance.value?.hide()
  }

  onMounted(() => {
    const modalEl = document.getElementById(modalId)
    if (modalEl) {
      modalInstance.value = new Modal(modalEl)
    }
  })

  onUnmounted(() => {
    modalInstance.value?.dispose()
  })

  return {
    show,
    hide,
    modalInstance,
  }
}
