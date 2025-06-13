import { Modal } from 'bootstrap'
import type { ModalAdapter, ModalController } from '../composables/use-modal'

interface BootstrapModalController extends ModalController {
  instance: Modal
}

export const bootstrapModalAdapter: ModalAdapter = {
  createModal(element: HTMLElement): BootstrapModalController {
    const instance = new Modal(element)

    return {
      instance,
      show: () => instance.show(),
      hide: () => instance.hide(),
      toggle: () => instance.toggle(),
    }
  },

  destroyModal(controller: ModalController): void {
    const bootstrapController = controller as BootstrapModalController
    bootstrapController.instance.dispose()
  },
}
