import { ref } from 'vue'
import { AlertType } from '../models/alert-type'
import { ApiError } from '../models/api-error'

export const useApiErrorHandler = () => {
  const alertMessage = ref('')
  const debugMessage = ref('')
  const validationErrors = ref<Record<string, string>>({})
  const alertType = ref<AlertType | null>(null)

  const handleApiError = (error: unknown) => {
    alertType.value = AlertType.ERROR
    if (error instanceof ApiError) {
      alertMessage.value = error.message
      debugMessage.value = error.debugMessage
      validationErrors.value = error.validationErrors
    } else {
      alertMessage.value = error instanceof Error ? error.message : 'An unexpected error occurred'
      debugMessage.value = ''
      validationErrors.value = {}
    }
  }

  const clearError = () => {
    alertMessage.value = ''
    debugMessage.value = ''
    validationErrors.value = {}
    alertType.value = null
  }

  const setSuccess = (message: string) => {
    alertType.value = AlertType.SUCCESS
    alertMessage.value = message
    debugMessage.value = ''
    validationErrors.value = {}
  }

  return {
    alertMessage,
    debugMessage,
    validationErrors,
    alertType,
    handleApiError,
    clearError,
    setSuccess,
  }
}

