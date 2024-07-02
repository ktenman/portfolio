/* eslint-disable no-unused-vars */
export enum AlertType {
  ERROR = 'error',
  SUCCESS = 'success',
}

export function getAlertBootstrapClass(type: AlertType | null): string {
  const mapping: Record<AlertType, string> = {
    [AlertType.ERROR]: 'alert-danger',
    [AlertType.SUCCESS]: 'alert-success',
  }
  return mapping[type ?? AlertType.ERROR] || 'alert-info'
}

/* eslint-enable no-unused-vars */
