import { onMounted, onUnmounted, ref } from 'vue'
import { Modal } from 'bootstrap'

export const useModal = (modalId: string) => {
  let modalInstance: Modal | null = null
  const isModalOpen = ref(false)

  onMounted(() => {
    const modalElement = document.getElementById(modalId)
    if (modalElement) {
      modalInstance = new Modal(modalElement)

      // Listen for modal show/hide events
      modalElement.addEventListener('show.bs.modal', () => {
        isModalOpen.value = true
      })

      modalElement.addEventListener('hide.bs.modal', () => {
        isModalOpen.value = false
      })
    }
  })

  onUnmounted(() => {
    if (modalInstance) {
      modalInstance.dispose()
      modalInstance = null
    }
  })

  const showModal = () => {
    modalInstance?.show()
  }

  const hideModal = () => {
    modalInstance?.hide()
  }

  return {
    showModal,
    hideModal,
    isModalOpen,
  }
}
