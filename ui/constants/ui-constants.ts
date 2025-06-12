export const ALERT_TYPES = {
  SUCCESS: 'success',
  DANGER: 'danger',
  WARNING: 'warning',
  INFO: 'info',
} as const

export type AlertType = (typeof ALERT_TYPES)[keyof typeof ALERT_TYPES]

export const MESSAGES = {
  DELETE_CONFIRMATION: 'Are you sure you want to delete this item?',
  SAVE_SUCCESS: 'Item saved successfully.',
  UPDATE_SUCCESS: 'Item updated successfully.',
  DELETE_SUCCESS: 'Item deleted successfully.',
  GENERIC_ERROR: 'An unexpected error occurred',
} as const
