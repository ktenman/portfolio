import { ref, Ref } from 'vue'
import { Modal } from 'bootstrap'
import { ApiError } from '../models/api-error'
import { useConfirm } from './use-confirm'
import { ALERT_TYPES, AlertType, MESSAGES } from '../constants/ui-constants'

interface UseCrudViewReturn<T> {
  selectedItem: Ref<Partial<T> | null>
  showAlert: Ref<boolean>
  alertType: Ref<AlertType>
  alertMessage: Ref<string>
  isConfirmOpen: Ref<boolean>
  confirmOptions: ReturnType<typeof useConfirm>['confirmOptions']
  initModal: () => void
  openAddModal: (initialState?: Partial<T>) => void
  openEditModal: (item: T) => void
  showSuccess: (message: string) => void
  showError: (error: unknown, defaultMessage?: string) => void
  handleSave: (
    itemToSave: Partial<T>,
    saveFn: (item: Partial<T>) => Promise<void>,
    onSuccess: () => void
  ) => Promise<void>
  handleDelete: (
    deleteFn: () => Promise<void>,
    onSuccess: () => void,
    confirmOptions?: {
      title?: string
      message?: string
      confirmText?: string
      confirmClass?: string
    }
  ) => Promise<void>
  confirm: (options?: Parameters<ReturnType<typeof useConfirm>['confirm']>[0]) => Promise<boolean>
  handleConfirm: () => void
  handleCancel: () => void
}

export function useCrudView<T extends { id?: number | string }>(
  modalElementId: string
): UseCrudViewReturn<T> {
  let modalInstance: Modal | null = null

  const selectedItem = ref<Partial<T> | null>(null) as Ref<Partial<T> | null>
  const showAlert = ref(false)
  const alertType = ref<AlertType>(ALERT_TYPES.SUCCESS)
  const alertMessage = ref('')

  const { isConfirmOpen, confirmOptions, confirm, handleConfirm, handleCancel } = useConfirm()

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
    alertType.value = ALERT_TYPES.SUCCESS
    alertMessage.value = message
    showAlert.value = true
  }

  const showError = (error: unknown, defaultMessage: string = MESSAGES.GENERIC_ERROR) => {
    alertType.value = ALERT_TYPES.DANGER
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
    saveFn: (item: Partial<T>) => Promise<void>,
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
    deleteFn: () => Promise<void>,
    onSuccess: () => void,
    confirmOpts?: {
      title?: string
      message?: string
      confirmText?: string
      confirmClass?: string
    }
  ) => {
    const shouldDelete = await confirm({
      title: confirmOpts?.title || 'Delete Confirmation',
      message: confirmOpts?.message || MESSAGES.DELETE_CONFIRMATION,
      confirmText: confirmOpts?.confirmText || 'Delete',
      cancelText: 'Cancel',
      confirmClass: confirmOpts?.confirmClass || 'btn-danger',
    })

    if (shouldDelete) {
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
    isConfirmOpen,
    confirmOptions,
    initModal,
    openAddModal,
    openEditModal,
    showSuccess,
    showError,
    handleSave,
    handleDelete,
    confirm,
    handleConfirm,
    handleCancel,
  }
}
