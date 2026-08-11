import { ref } from 'vue'

export type ToastType = 'success' | 'error' | 'info' | 'warning'

interface ToastItem {
  id: number
  message: string
  type: ToastType
}

const DURATIONS: Record<ToastType, number> = {
  success: 4000,
  error: 7500,
  info: 5000,
  warning: 6000,
}

export const toasts = ref<ToastItem[]>([])

let nextId = 0

export const dismissToast = (id: number) => {
  toasts.value = toasts.value.filter(toast => toast.id !== id)
}

const showToast = (message: string, type: ToastType) => {
  const id = nextId++
  toasts.value.push({ id, message, type })
  setTimeout(() => dismissToast(id), DURATIONS[type])
}

export const useToast = () => ({
  success: (message: string) => showToast(message, 'success'),
  error: (message: string) => showToast(message, 'error'),
  info: (message: string) => showToast(message, 'info'),
  warning: (message: string) => showToast(message, 'warning'),
})
