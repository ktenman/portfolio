export const ALERT_TYPES = {
  SUCCESS: 'success',
  DANGER: 'danger',
  WARNING: 'warning',
  INFO: 'info',
} as const

export type AlertType = (typeof ALERT_TYPES)[keyof typeof ALERT_TYPES]
