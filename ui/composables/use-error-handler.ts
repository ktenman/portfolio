import { ref, Ref, computed } from 'vue'
import { ApiError } from '../models/api-error'
import { AlertType } from '../models/alert-type'

interface ErrorHandlerOptions {
  defaultMessage?: string
  showAlert?: boolean
  logErrors?: boolean
}

interface UseErrorHandlerReturn {
  error: Ref<ApiError | null>
  errorMessage: Ref<string>
  hasError: Ref<boolean>
  showAlert: Ref<boolean>
  alertType: Ref<AlertType>
  alertMessage: Ref<string>
  handleError: (error: ApiError | Error | unknown, customMessage?: string) => void
  clearError: () => void
  setError: (message: string) => void
}

export function useErrorHandler(options: ErrorHandlerOptions = {}): UseErrorHandlerReturn {
  const {
    defaultMessage = 'An error occurred',
    showAlert: showAlertOption = true,
    logErrors = true,
  } = options

  const error = ref<ApiError | null>(null)
  const showAlert = ref(false)
  const alertType = ref<AlertType>(AlertType.ERROR)
  const alertMessage = ref('')

  const errorMessage = computed(() => {
    if (!error.value) return ''
    return error.value.message || defaultMessage
  })

  const hasError = computed(() => error.value !== null)

  const handleError = (err: ApiError | Error | unknown, customMessage?: string) => {
    if (logErrors) {
      console.error('Error handled:', err)
    }

    if (err instanceof ApiError) {
      error.value = err
      alertMessage.value = customMessage || err.message || defaultMessage
    } else if (err instanceof Error) {
      error.value = new ApiError(500, err.message, err.message, {})
      alertMessage.value = customMessage || err.message || defaultMessage
    } else {
      error.value = new ApiError(500, defaultMessage, String(err), {})
      alertMessage.value = customMessage || defaultMessage
    }

    if (showAlertOption) {
      showAlert.value = true
      alertType.value = AlertType.ERROR
    }
  }

  const clearError = () => {
    error.value = null
    showAlert.value = false
    alertMessage.value = ''
  }

  const setError = (message: string) => {
    error.value = new ApiError(400, message, message, {})
    alertMessage.value = message

    if (showAlertOption) {
      showAlert.value = true
      alertType.value = AlertType.ERROR
    }
  }

  return {
    error,
    errorMessage,
    hasError,
    showAlert,
    alertType,
    alertMessage,
    handleError,
    clearError,
    setError,
  }
}
