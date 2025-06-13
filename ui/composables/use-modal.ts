import { onMounted, onUnmounted, ref } from 'vue'

export interface ModalController {
  show: () => void
  hide: () => void
  toggle: () => void
}

export interface ModalAdapter {
  createModal: (element: HTMLElement) => ModalController
  destroyModal: (controller: ModalController) => void
}

let modalAdapter: ModalAdapter | null = null

export function setModalAdapter(adapter: ModalAdapter) {
  modalAdapter = adapter
}

export function useModal(modalId: string) {
  const modalController = ref<ModalController | null>(null)
  const isVisible = ref(false)

  const show = () => {
    modalController.value?.show()
    isVisible.value = true
  }

  const hide = () => {
    modalController.value?.hide()
    isVisible.value = false
  }

  const toggle = () => {
    if (isVisible.value) {
      hide()
    } else {
      show()
    }
  }

  onMounted(() => {
    const modalEl = document.getElementById(modalId)
    if (modalEl && modalAdapter) {
      modalController.value = modalAdapter.createModal(modalEl)
    }
  })

  onUnmounted(() => {
    if (modalController.value && modalAdapter) {
      modalAdapter.destroyModal(modalController.value)
    }
  })

  return {
    show,
    hide,
    toggle,
    isVisible,
  }
}
