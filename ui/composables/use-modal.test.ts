import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, onMounted } from 'vue'
import { useModal } from './use-modal'

// Mock Bootstrap Modal with proper hoisting
vi.mock('bootstrap', () => {
  const MockModal = vi.fn().mockImplementation(function (this: any, element: HTMLElement) {
    this.element = element
    this.show = vi.fn()
    this.hide = vi.fn()
    this.dispose = vi.fn()
  })

  return {
    Modal: MockModal,
  }
})

// Import the mocked Modal to get access to the mock
import { Modal } from 'bootstrap'
const MockModal = Modal as any

describe('useModal', () => {
  const modalId = 'test-modal'

  beforeEach(() => {
    // Clear document body
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  describe('initialization', () => {
    it('initializes with correct default state', () => {
      const TestComponent = defineComponent({
        setup() {
          const { isModalOpen } = useModal(modalId)
          return { isModalOpen }
        },
        template: '<div></div>',
      })

      const wrapper = mount(TestComponent)
      const vm = wrapper.vm as any

      expect(vm.isModalOpen).toBe(false)
    })

    it('creates modal instance when element exists', () => {
      // Create modal element
      const modalElement = document.createElement('div')
      modalElement.id = modalId
      document.body.appendChild(modalElement)

      const TestComponent = defineComponent({
        setup() {
          return useModal(modalId)
        },
        template: '<div></div>',
      })

      mount(TestComponent)

      expect(MockModal).toHaveBeenCalledWith(modalElement)
    })

    it('does not create modal instance when element does not exist', () => {
      const TestComponent = defineComponent({
        setup() {
          return useModal('non-existent-modal')
        },
        template: '<div></div>',
      })

      mount(TestComponent)

      expect(MockModal).not.toHaveBeenCalled()
    })
  })

  describe('event listeners', () => {
    it('sets isModalOpen to true on show.bs.modal event', () => {
      const modalElement = document.createElement('div')
      modalElement.id = modalId
      document.body.appendChild(modalElement)

      const TestComponent = defineComponent({
        setup() {
          const modal = useModal(modalId)
          return { modal }
        },
        template: '<div></div>',
      })

      const wrapper = mount(TestComponent)
      const vm = wrapper.vm as any

      // Simulate show event
      const showEvent = new Event('show.bs.modal')
      modalElement.dispatchEvent(showEvent)

      expect(vm.modal.isModalOpen.value).toBe(true)
    })

    it('sets isModalOpen to false on hide.bs.modal event', () => {
      const modalElement = document.createElement('div')
      modalElement.id = modalId
      document.body.appendChild(modalElement)

      const TestComponent = defineComponent({
        setup() {
          const modal = useModal(modalId)
          return { modal }
        },
        template: '<div></div>',
      })

      const wrapper = mount(TestComponent)
      const vm = wrapper.vm as any

      // First show the modal
      const showEvent = new Event('show.bs.modal')
      modalElement.dispatchEvent(showEvent)
      expect(vm.modal.isModalOpen.value).toBe(true)

      // Then hide it
      const hideEvent = new Event('hide.bs.modal')
      modalElement.dispatchEvent(hideEvent)
      expect(vm.modal.isModalOpen.value).toBe(false)
    })
  })

  describe('showModal', () => {
    it('calls show on modal instance', () => {
      const modalElement = document.createElement('div')
      modalElement.id = modalId
      document.body.appendChild(modalElement)

      const TestComponent = defineComponent({
        setup() {
          const modal = useModal(modalId)
          // Call showModal after component is mounted
          onMounted(() => {
            modal.showModal()
          })
          return { modal }
        },
        template: '<div></div>',
      })

      mount(TestComponent)

      // Get the instance from the mock
      const modalInstances = MockModal.mock.instances
      expect(modalInstances.length).toBeGreaterThan(0)
      expect(modalInstances[modalInstances.length - 1].show).toHaveBeenCalled()
    })

    it('does nothing when modal instance does not exist', () => {
      const TestComponent = defineComponent({
        setup() {
          const modal = useModal('non-existent-modal')
          onMounted(() => {
            modal.showModal()
          })
          return { modal }
        },
        template: '<div></div>',
      })

      mount(TestComponent)

      expect(MockModal).not.toHaveBeenCalled()
    })
  })

  describe('hideModal', () => {
    it('calls hide on modal instance', () => {
      const modalElement = document.createElement('div')
      modalElement.id = modalId
      document.body.appendChild(modalElement)

      const TestComponent = defineComponent({
        setup() {
          const modal = useModal(modalId)
          onMounted(() => {
            modal.hideModal()
          })
          return { modal }
        },
        template: '<div></div>',
      })

      mount(TestComponent)

      // Get the instance from the mock
      const modalInstances = MockModal.mock.instances
      expect(modalInstances.length).toBeGreaterThan(0)
      expect(modalInstances[modalInstances.length - 1].hide).toHaveBeenCalled()
    })

    it('does nothing when modal instance does not exist', () => {
      const TestComponent = defineComponent({
        setup() {
          const modal = useModal('non-existent-modal')
          onMounted(() => {
            modal.hideModal()
          })
          return { modal }
        },
        template: '<div></div>',
      })

      mount(TestComponent)

      expect(MockModal).not.toHaveBeenCalled()
    })
  })

  describe('cleanup', () => {
    it('disposes modal instance on unmount', () => {
      const modalElement = document.createElement('div')
      modalElement.id = modalId
      document.body.appendChild(modalElement)

      const TestComponent = defineComponent({
        setup() {
          return useModal(modalId)
        },
        template: '<div></div>',
      })

      const wrapper = mount(TestComponent)
      wrapper.unmount()

      // Get the instance from the mock
      const modalInstances = MockModal.mock.instances
      expect(modalInstances.length).toBeGreaterThan(0)
      expect(modalInstances[modalInstances.length - 1].dispose).toHaveBeenCalled()
    })

    it('handles unmount when modal instance does not exist', () => {
      const TestComponent = defineComponent({
        setup() {
          return useModal('non-existent-modal')
        },
        template: '<div></div>',
      })

      const wrapper = mount(TestComponent)

      expect(() => wrapper.unmount()).not.toThrow()
      expect(MockModal).not.toHaveBeenCalled()
    })
  })

  describe('return values', () => {
    it('returns all expected functions and refs', () => {
      const TestComponent = defineComponent({
        setup() {
          const modal = useModal(modalId)
          return { modal }
        },
        template: '<div></div>',
      })

      const wrapper = mount(TestComponent)
      const vm = wrapper.vm as any

      expect(typeof vm.modal.showModal).toBe('function')
      expect(typeof vm.modal.hideModal).toBe('function')
      expect(vm.modal.isModalOpen).toBeDefined()
      expect(typeof vm.modal.isModalOpen.value).toBe('boolean')
    })
  })

  describe('multiple modals', () => {
    it('handles multiple modal instances independently', () => {
      const modal1Element = document.createElement('div')
      modal1Element.id = 'modal1'
      document.body.appendChild(modal1Element)

      const modal2Element = document.createElement('div')
      modal2Element.id = 'modal2'
      document.body.appendChild(modal2Element)

      const TestComponent = defineComponent({
        setup() {
          const modal1 = useModal('modal1')
          const modal2 = useModal('modal2')
          return { modal1, modal2 }
        },
        template: '<div></div>',
      })

      const wrapper = mount(TestComponent)
      const vm = wrapper.vm as any

      expect(vm.modal1.isModalOpen.value).toBe(false)
      expect(vm.modal2.isModalOpen.value).toBe(false)

      // Show modal1
      const showEvent1 = new Event('show.bs.modal')
      modal1Element.dispatchEvent(showEvent1)

      expect(vm.modal1.isModalOpen.value).toBe(true)
      expect(vm.modal2.isModalOpen.value).toBe(false)

      // Show modal2
      const showEvent2 = new Event('show.bs.modal')
      modal2Element.dispatchEvent(showEvent2)

      expect(vm.modal1.isModalOpen.value).toBe(true)
      expect(vm.modal2.isModalOpen.value).toBe(true)
    })
  })
})
