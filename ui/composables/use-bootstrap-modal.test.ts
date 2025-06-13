import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { useBootstrapModal } from './use-bootstrap-modal'
import { setModalAdapter } from './use-modal'

describe('useBootstrapModal', () => {
  let mockModalController: any
  let mockAdapter: any

  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''

    mockModalController = {
      show: vi.fn(),
      hide: vi.fn(),
      toggle: vi.fn(),
    }

    mockAdapter = {
      createModal: vi.fn(() => mockModalController),
      destroyModal: vi.fn(),
    }

    setModalAdapter(mockAdapter)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  const createComponent = (modalId: string) => {
    return mount(
      defineComponent({
        setup() {
          const modal = useBootstrapModal(modalId)
          return { modal }
        },
        template: `<div><div :id="modalId" class="modal">Modal Content</div></div>`,
        props: {
          modalId: {
            type: String,
            default: modalId,
          },
        },
      }),
      {
        attachTo: document.body,
      }
    )
  }

  it('should initialize modal instance on mount', async () => {
    const modalId = 'test-modal'
    createComponent(modalId)

    await nextTick()

    expect(mockAdapter.createModal).toHaveBeenCalledWith(document.getElementById(modalId))
  })

  it('should not initialize modal if element not found', async () => {
    mount(
      defineComponent({
        setup() {
          const modal = useBootstrapModal('non-existent-modal')
          return { modal }
        },
        template: '<div>No modal element</div>',
      })
    )

    await nextTick()

    expect(mockAdapter.createModal).not.toHaveBeenCalled()
  })

  it('should show modal when show is called', async () => {
    const modalId = 'show-modal'
    const wrapper = createComponent(modalId)

    await nextTick()

    wrapper.vm.modal.show()

    expect(mockModalController.show).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.modal.isVisible.value).toBe(true)
  })

  it('should hide modal when hide is called', async () => {
    const modalId = 'hide-modal'
    const wrapper = createComponent(modalId)

    await nextTick()

    wrapper.vm.modal.show()
    wrapper.vm.modal.hide()

    expect(mockModalController.hide).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.modal.isVisible.value).toBe(false)
  })

  it('should dispose modal on unmount', async () => {
    const modalId = 'dispose-modal'
    const wrapper = createComponent(modalId)

    await nextTick()

    wrapper.unmount()

    expect(mockAdapter.destroyModal).toHaveBeenCalledWith(mockModalController)
  })

  it('should handle show/hide when modal instance is null', async () => {
    const wrapper = mount(
      defineComponent({
        setup() {
          const modal = useBootstrapModal('non-existent')
          return { modal }
        },
        template: '<div>Test</div>',
      })
    )

    await nextTick()

    expect(() => {
      wrapper.vm.modal.show()
      wrapper.vm.modal.hide()
    }).not.toThrow()

    expect(mockModalController.show).not.toHaveBeenCalled()
    expect(mockModalController.hide).not.toHaveBeenCalled()
  })

  it('should work with dynamic modal id', async () => {
    const wrapper = mount(
      defineComponent({
        setup() {
          const modalId = 'dynamic-modal'
          const modal = useBootstrapModal(modalId)
          return { modal, modalId }
        },
        template: '<div><div :id="modalId">Dynamic Modal</div></div>',
      }),
      {
        attachTo: document.body,
      }
    )

    await nextTick()

    expect(mockAdapter.createModal).toHaveBeenCalled()

    wrapper.vm.modal.show()
    expect(mockModalController.show).toHaveBeenCalledTimes(1)
  })
})
