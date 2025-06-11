import { ref, Ref } from 'vue'
import { Modal } from 'bootstrap'
import { ApiError } from '../models/api-error'

interface UseCrudViewReturn<T> {
  selectedItem: Ref<Partial<T>>
  showAlert: Ref<boolean>
  alertType: Ref<'success' | 'danger'>
  alertMessage: Ref<string>
  initModal: () => void
  openAddModal: (initialState?: Partial<T>) => void
  openEditModal: (item: T) => void
  showSuccess: (message: string) => void
  showError: (error: unknown, defaultMessage?: string) => void
  handleSave: (
    itemToSave: Partial<T>,
    saveFn: (item: Partial<T>) => Promise<any>,
    onSuccess: () => void
  ) => Promise<void>
  handleDelete: (
    deleteFn: () => Promise<any>,
    onSuccess: () => void,
    confirmMessage?: string
  ) => Promise<void>
}

export function useCrudView<T extends { id?: any }>(
  modalElementId: string
): UseCrudViewReturn<T> {
  let modalInstance: Modal | null = null
  
  const selectedItem = ref<Partial<T>>({}) as Ref<Partial<T>>
  const showAlert = ref(false)
  const alertType = ref<'success' | 'danger'>('success')
  const alertMessage = ref('')

  const initModal = () => {
    const modalEl = document.getElementById(modalElementId)
    if (modalEl) {
      modalInstance = new Modal(modalEl)
    }
  }

  const openAddModal = (initialState: Partial<T> = {}) => {
    selectedItem.value = initialState
    modalInstance?.show()
  }

  const openEditModal = (item: T) => {
    selectedItem.value = { ...item }
    modalInstance?.show()
  }

  const showSuccess = (message: string) => {
    alertType.value = 'success'
    alertMessage.value = message
    showAlert.value = true
  }

  const showError = (error: unknown, defaultMessage = 'An unexpected error occurred') => {
    alertType.value = 'danger'
    if (error instanceof ApiError) {
      alertMessage.value = error.message
    } else if (error instanceof Error) {
      alertMessage.value = error.message
    } else {
      alertMessage.value = defaultMessage
    }
    showAlert.value = true
  }

  const handleSave = async (
    itemToSave: Partial<T>,
    saveFn: (item: Partial<T>) => Promise<any>,
    onSuccess: () => void
  ) => {
    try {
      await saveFn(itemToSave)
      modalInstance?.hide()
      onSuccess()
    } catch (error) {
      showError(error)
    }
  }

  const handleDelete = async (
    deleteFn: () => Promise<any>,
    onSuccess: () => void,
    confirmMessage = 'Are you sure you want to delete this item?'
  ) => {
    if (confirm(confirmMessage)) {
      try {
        await deleteFn()
        onSuccess()
      } catch (error) {
        showError(error)
      }
    }
  }

  return {
    selectedItem,
    showAlert,
    alertType,
    alertMessage,
    initModal,
    openAddModal,
    openEditModal,
    showSuccess,
    showError,
    handleSave,
    handleDelete,
  }
}