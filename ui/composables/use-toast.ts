import { Toast } from 'bootstrap'

let toastContainer: HTMLElement | null = null

const ensureToastContainer = () => {
  if (!toastContainer) {
    toastContainer = document.createElement('div')
    toastContainer.className = 'toast-container position-fixed top-0 end-0 p-3'
    toastContainer.style.zIndex = '9999'
    document.body.appendChild(toastContainer)
  }
  return toastContainer
}

const createToast = (
  message: string,
  type: 'success' | 'error' | 'info' | 'warning',
  duration = 10000
) => {
  const container = ensureToastContainer()

  const typeConfig = {
    success: { bg: 'bg-success', icon: '✓', title: 'Success' },
    error: { bg: 'bg-danger', icon: '✕', title: 'Error' },
    info: { bg: 'bg-info', icon: 'ℹ', title: 'Info' },
    warning: { bg: 'bg-warning', icon: '⚠', title: 'Warning' },
  }

  const config = typeConfig[type]

  const toastEl = document.createElement('div')
  toastEl.className = `toast align-items-center text-white border-0 ${config.bg}`
  toastEl.setAttribute('role', 'alert')
  toastEl.setAttribute('aria-live', 'assertive')
  toastEl.setAttribute('aria-atomic', 'true')

  toastEl.innerHTML = `
    <div class="d-flex">
      <div class="toast-body">
        <strong>${config.icon} ${config.title}:</strong> ${message}
      </div>
      <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
    </div>
  `

  container.appendChild(toastEl)

  const bsToast = new Toast(toastEl, {
    autohide: true,
    delay: duration,
  })

  bsToast.show()

  toastEl.addEventListener('hidden.bs.toast', () => {
    toastEl.remove()
  })
}

export const useToast = () => {
  return {
    success: (message: string) => createToast(message, 'success', 4000),
    error: (message: string) => createToast(message, 'error', 7500),
    info: (message: string) => createToast(message, 'info', 5000),
    warning: (message: string) => createToast(message, 'warning', 6000),
  }
}
