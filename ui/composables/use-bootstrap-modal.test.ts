import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { useBootstrapModal } from './use-bootstrap-modal'
import { Modal } from 'bootstrap'

vi.mock('bootstrap', () => ({
  Modal: vi.fn().mockImplementation(function () {
    return {
      show: vi.fn(),
      hide: vi.fn(),
      toggle: vi.fn(),
      dispose: vi.fn(),
    }
  }),
}))

describe('useBootstrapModal', () => {
  let mockModalInstance: any

  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''

    mockModalInstance = {
      show: vi.fn(),
      hide: vi.fn(),
      toggle: vi.fn(),
      dispose: vi.fn(),
    }

    const MockModal = vi.mocked(Modal)
    MockModal.mockImplementation(function () {
      return mockModalInstance
    })
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

    expect(Modal).toHaveBeenCalledWith(document.getElementById(modalId))
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

    expect(Modal).not.toHaveBeenCalled()
  })

  it('should show modal when show is called', async () => {
    const modalId = 'show-modal'
    const wrapper = createComponent(modalId)

    await nextTick()

    wrapper.vm.modal.show()

    expect(mockModalInstance.show).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.modal.isVisible.value).toBe(true)
  })

  it('should hide modal when hide is called', async () => {
    const modalId = 'hide-modal'
    const wrapper = createComponent(modalId)

    await nextTick()

    wrapper.vm.modal.show()
    wrapper.vm.modal.hide()

    expect(mockModalInstance.hide).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.modal.isVisible.value).toBe(false)
  })

  it('should toggle modal when toggle is called', async () => {
    const modalId = 'toggle-modal'
    const wrapper = createComponent(modalId)

    await nextTick()

    wrapper.vm.modal.toggle()

    expect(mockModalInstance.toggle).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.modal.isVisible.value).toBe(true)
  })

  it('should dispose modal on unmount', async () => {
    const modalId = 'dispose-modal'
    const wrapper = createComponent(modalId)

    await nextTick()

    wrapper.unmount()

    expect(mockModalInstance.dispose).toHaveBeenCalledWith()
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
      wrapper.vm.modal.toggle()
    }).not.toThrow()
  })

  it('should update visibility state on Bootstrap events', async () => {
    const modalId = 'event-modal'
    const wrapper = createComponent(modalId)

    await nextTick()

    const modalEl = document.getElementById(modalId)

    modalEl?.dispatchEvent(new Event('shown.bs.modal'))
    await nextTick()
    expect(wrapper.vm.modal.isVisible.value).toBe(true)

    modalEl?.dispatchEvent(new Event('hidden.bs.modal'))
    await nextTick()
    expect(wrapper.vm.modal.isVisible.value).toBe(false)
  })
})
