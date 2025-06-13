import { ref, Ref, provide, inject } from 'vue'

interface ConfirmOptions {
  title?: string
  message?: string
  confirmText?: string
  cancelText?: string
  confirmClass?: string
}

interface ConfirmState {
  isOpen: Ref<boolean>
  options: Ref<ConfirmOptions>
  handleConfirm: () => void
  handleCancel: () => void
}

interface UseConfirmReturn {
  confirm: (options?: ConfirmOptions) => Promise<boolean>
}

const CONFIRM_KEY = Symbol('confirm')

export function provideConfirm(): ConfirmState {
  const isOpen = ref(false)
  const options = ref<ConfirmOptions>({})

  let currentResolver: ((value: boolean) => void) | null = null

  const cleanupResolver = () => {
    currentResolver = null
    isOpen.value = false
  }

  const handleConfirm = () => {
    if (currentResolver) {
      currentResolver(true)
      cleanupResolver()
    }
  }

  const handleCancel = () => {
    if (currentResolver) {
      currentResolver(false)
      cleanupResolver()
    }
  }

  const confirm = (confirmOptions: ConfirmOptions = {}): Promise<boolean> => {
    if (currentResolver) {
      currentResolver(false)
      currentResolver = null
    }

    options.value = {
      title: 'Confirm',
      message: 'Are you sure?',
      confirmText: 'Confirm',
      cancelText: 'Cancel',
      confirmClass: 'btn-primary',
      ...confirmOptions,
    }

    isOpen.value = true

    return new Promise<boolean>(resolve => {
      currentResolver = resolve
    })
  }

  const state = {
    isOpen,
    options,
    handleConfirm,
    handleCancel,
  }

  provide(CONFIRM_KEY, confirm)

  return state
}

export function useConfirm(): UseConfirmReturn {
  const confirm = inject<(options?: ConfirmOptions) => Promise<boolean>>(CONFIRM_KEY)

  if (!confirm) {
    throw new Error(
      'useConfirm must be used within a component tree that has called provideConfirm'
    )
  }

  return { confirm }
}
