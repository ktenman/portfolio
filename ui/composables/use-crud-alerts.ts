import { ref } from 'vue'
import { ALERT_TYPES, AlertType } from '../constants/ui-constants'

export function useCrudAlerts() {
  const showAlert = ref(false)
  const alertType = ref<AlertType>(ALERT_TYPES.SUCCESS)
  const alertMessage = ref('')

  const showSuccess = (message: string) => {
    alertType.value = ALERT_TYPES.SUCCESS
    alertMessage.value = message
    showAlert.value = true
  }

  const showError = (message: string) => {
    alertType.value = ALERT_TYPES.DANGER
    alertMessage.value = message
    showAlert.value = true
  }

  return {
    showAlert,
    alertType,
    alertMessage,
    showSuccess,
    showError,
  }
}
