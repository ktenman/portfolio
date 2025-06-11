import { ref, Ref } from 'vue'

interface ConfirmOptions {
  title?: string
  message?: string
  confirmText?: string
  cancelText?: string
  confirmClass?: string
}

interface UseConfirmReturn {
  isConfirmOpen: Ref<boolean>
  confirmOptions: Ref<ConfirmOptions>
  confirm: (options?: ConfirmOptions) => Promise<boolean>
  handleConfirm: () => void
  handleCancel: () => void
}

export function useConfirm(): UseConfirmReturn {
  const isConfirmOpen = ref(false)
  const confirmOptions = ref<ConfirmOptions>({})

  let resolvePromise: ((value: boolean) => void) | null = null

  const confirm = (options: ConfirmOptions = {}): Promise<boolean> => {
    confirmOptions.value = {
      title: 'Confirm',
      message: 'Are you sure?',
      confirmText: 'Confirm',
      cancelText: 'Cancel',
      confirmClass: 'btn-primary',
      ...options,
    }

    isConfirmOpen.value = true

    return new Promise<boolean>(resolve => {
      resolvePromise = resolve
    })
  }

  const handleConfirm = () => {
    if (resolvePromise) {
      resolvePromise(true)
      resolvePromise = null
    }
    isConfirmOpen.value = false
  }

  const handleCancel = () => {
    if (resolvePromise) {
      resolvePromise(false)
      resolvePromise = null
    }
    isConfirmOpen.value = false
  }

  return {
    isConfirmOpen,
    confirmOptions,
    confirm,
    handleConfirm,
    handleCancel,
  }
}
