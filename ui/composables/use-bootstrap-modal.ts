import { onMounted, onUnmounted, ref } from 'vue'
import { Modal } from 'bootstrap'

export function useBootstrapModal(modalId: string) {
  const modalInstance = ref<Modal | null>(null)
  const isVisible = ref(false)

  const show = () => {
    modalInstance.value?.show()
    isVisible.value = true
  }

  const hide = () => {
    modalInstance.value?.hide()
    isVisible.value = false
  }

  const toggle = () => {
    modalInstance.value?.toggle()
    isVisible.value = !isVisible.value
  }

  onMounted(() => {
    const modalEl = document.getElementById(modalId)
    if (modalEl) {
      modalInstance.value = new Modal(modalEl)

      modalEl.addEventListener('shown.bs.modal', () => {
        isVisible.value = true
      })

      modalEl.addEventListener('hidden.bs.modal', () => {
        isVisible.value = false
      })
    }
  })

  onUnmounted(() => {
    modalInstance.value?.dispose()
  })

  return {
    show,
    hide,
    toggle,
    isVisible,
  }
}
