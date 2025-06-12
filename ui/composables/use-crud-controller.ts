import { computed, onMounted, ref, Ref, watch } from 'vue'
import { Modal } from 'bootstrap'
import { ApiError } from '../models/api-error'
import { ICrudService } from '../types/service-interfaces'
import { ALERT_TYPES, AlertType, MESSAGES } from '../constants/ui-constants'
import { useConfirm } from './use-confirm'

type AsyncState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T[] }
  | { status: 'error'; error: Error }

export interface CrudControllerOptions<T> {
  service: ICrudService<T>
  modalRef?: Ref<HTMLElement | null>
  modalId?: string
  loadDataFn?: () => Promise<T[]>
  onAfterCreate?: (newItem: T) => void
  onAfterUpdate?: (updatedItem: T) => void
  onAfterDelete?: (id: string | number) => void
  onError?: (error: Error) => void
}

export interface UseCrudControllerReturn<T> {
  items: Ref<T[]>
  selectedItem: Ref<Partial<T> | null>
  isLoading: Ref<boolean>
  isCreating: Ref<boolean>
  isUpdating: Ref<boolean>
  isDeleting: Ref<boolean>
  error: Ref<Error | null>
  state: Ref<AsyncState<T>>

  showAlert: Ref<boolean>
  alertType: Ref<AlertType>
  alertMessage: Ref<string>
  isConfirmOpen: Ref<boolean>
  confirmOptions: ReturnType<typeof useConfirm>['confirmOptions']

  fetchAll: () => Promise<void>
  select: (item: T | null) => void
  openAddModal: (initialState?: Partial<T>) => void
  openEditModal: (item: T) => void
  handleSave: (item: Partial<T>) => Promise<void>
  handleDelete: (id: number | string) => Promise<void>

  showSuccess: (message: string) => void
  showError: (error: unknown, defaultMessage?: string) => void
  confirm: () => Promise<boolean>
  handleConfirm: () => void
  handleCancel: () => void
}

export function useCrudController<T extends { id?: number | string }>(
  options: CrudControllerOptions<T>
): UseCrudControllerReturn<T> {
  const {
    service,
    modalRef,
    modalId,
    loadDataFn,
    onAfterCreate,
    onAfterUpdate,
    onAfterDelete,
    onError,
  } = options

  const state = ref<AsyncState<T>>({ status: 'idle' }) as Ref<AsyncState<T>>

  const items = computed(() => (state.value.status === 'success' ? state.value.data : []))

  const selectedItem = ref<Partial<T> | null>(null) as Ref<Partial<T> | null>
  const isCreating = ref(false)
  const isUpdating = ref(false)
  const isDeleting = ref(false)

  const isLoading = computed(() => state.value.status === 'loading')
  const error = computed(() => (state.value.status === 'error' ? state.value.error : null))

  const showAlert = ref(false)
  const alertType = ref<AlertType>(ALERT_TYPES.SUCCESS)
  const alertMessage = ref('')

  const modalInstance = ref<Modal | null>(null)

  const initModal = () => {
    if (modalId) {
      const modalEl = document.getElementById(modalId)
      if (modalEl) {
        modalInstance.value = new Modal(modalEl)
      }
    } else if (modalRef) {
      watch(
        modalRef,
        el => {
          if (el) {
            const modalElement = el.querySelector('.modal') || el
            modalInstance.value = new Modal(modalElement)
          } else {
            modalInstance.value?.dispose()
            modalInstance.value = null
          }
        },
        { immediate: true }
      )
    }
  }

  const { isConfirmOpen, confirmOptions, confirm, handleConfirm, handleCancel } = useConfirm()
  const fetchAll = async () => {
    state.value = { status: 'loading' }
    try {
      const data = loadDataFn ? await loadDataFn() : await service.getAll()
      state.value = { status: 'success', data }
    } catch (err) {
      const error = err instanceof Error ? err : new Error('Failed to fetch data')
      state.value = { status: 'error', error }
      if (onError) onError(error)
      else showError(error)
    }
  }

  const select = (item: T | null) => {
    selectedItem.value = item ? { ...item } : null
  }

  const openAddModal = (initialState: Partial<T> = {}) => {
    selectedItem.value = initialState
    modalInstance.value?.show()
  }

  const openEditModal = (item: T) => {
    selectedItem.value = { ...item }
    modalInstance.value?.show()
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

  const handleSave = async (item: Partial<T>) => {
    const isUpdate = !!item.id

    try {
      if (isUpdate) {
        isUpdating.value = true
        const result = await service.update(item.id!, item)
        modalInstance.value?.hide()
        showSuccess(MESSAGES.UPDATE_SUCCESS)
        if (onAfterUpdate) onAfterUpdate(result)
        await fetchAll()
      } else {
        isCreating.value = true
        const result = await service.create(item)
        modalInstance.value?.hide()
        showSuccess(MESSAGES.SAVE_SUCCESS)
        if (onAfterCreate) onAfterCreate(result)
        await fetchAll()
      }
    } catch (err) {
      const error = err instanceof Error ? err : new Error('Save failed')
      if (onError) onError(error)
      else showError(error)
    } finally {
      isCreating.value = false
      isUpdating.value = false
    }
  }

  const handleDelete = async (id: number | string) => {
    const shouldDelete = await confirm({
      title: 'Delete Confirmation',
      message: MESSAGES.DELETE_CONFIRMATION,
      confirmText: 'Delete',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger',
    })

    if (shouldDelete) {
      try {
        isDeleting.value = true
        await service.delete(id)
        showSuccess(MESSAGES.DELETE_SUCCESS)
        if (onAfterDelete) onAfterDelete(id)
        await fetchAll()
      } catch (err) {
        const error = err instanceof Error ? err : new Error('Delete failed')
        if (onError) onError(error)
        else showError(error)
      } finally {
        isDeleting.value = false
      }
    }
  }

  onMounted(() => {
    if (state.value.status === 'idle') {
      fetchAll()
    }
    initModal()
  })

  return {
    items,
    selectedItem,
    isLoading,
    isCreating,
    isUpdating,
    isDeleting,
    error,
    state,

    showAlert,
    alertType,
    alertMessage,
    isConfirmOpen,
    confirmOptions,

    fetchAll,
    select,
    openAddModal,
    openEditModal,
    handleSave,
    handleDelete,

    showSuccess,
    showError,
    confirm,
    handleConfirm,
    handleCancel,
  }
}
