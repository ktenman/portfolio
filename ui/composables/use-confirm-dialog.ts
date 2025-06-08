import { ref } from 'vue'

export interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: 'danger' | 'warning' | 'info'
}

export const useConfirmDialog = () => {
  const isOpen = ref(false)
  const options = ref<ConfirmOptions>({
    message: '',
    title: 'Confirm',
    confirmText: 'OK',
    cancelText: 'Cancel',
    type: 'info',
  })
  const resolvePromise = ref<((value: boolean) => void) | null>(null)

  const confirm = (confirmOptions: ConfirmOptions): Promise<boolean> => {
    return new Promise(resolve => {
      options.value = {
        ...options.value,
        ...confirmOptions,
      }
      isOpen.value = true
      resolvePromise.value = resolve
    })
  }

  const handleConfirm = () => {
    isOpen.value = false
    if (resolvePromise.value) {
      resolvePromise.value(true)
      resolvePromise.value = null
    }
  }

  const handleCancel = () => {
    isOpen.value = false
    if (resolvePromise.value) {
      resolvePromise.value(false)
      resolvePromise.value = null
    }
  }

  return {
    isOpen,
    options,
    confirm,
    handleConfirm,
    handleCancel,
  }
}

// Helper function for common delete confirmation
export const confirmDelete = (itemName = 'this item') => {
  const { confirm } = useConfirmDialog()
  return confirm({
    title: 'Delete Confirmation',
    message: `Are you sure you want to delete ${itemName}?`,
    confirmText: 'Delete',
    cancelText: 'Cancel',
    type: 'danger',
  })
}
