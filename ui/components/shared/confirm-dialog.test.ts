import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ConfirmDialog from './confirm-dialog.vue'

vi.mock('bootstrap', () => ({
  Modal: vi.fn().mockImplementation(() => ({
    show: vi.fn(),
    hide: vi.fn(),
    dispose: vi.fn(),
  })),
}))

describe('ConfirmDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const createWrapper = (props = {}) => {
    return mount(ConfirmDialog, {
      props: {
        modelValue: false,
        ...props,
      },
      attachTo: document.body,
    })
  }

  describe('visibility control', () => {
    it('should be hidden by default', () => {
      const wrapper = createWrapper()
      const modal = wrapper.find('.modal')

      expect(modal.classes()).not.toContain('show')
      expect((modal.element as HTMLElement).style.display).toBe('none')
    })

    it('should show when modelValue is true', async () => {
      const wrapper = createWrapper({ modelValue: true })
      await nextTick()

      const modal = wrapper.find('.modal')
      expect(modal.classes()).toContain('show')
      expect((modal.element as HTMLElement).style.display).toBe('block')
    })

    it('should toggle visibility when modelValue changes', async () => {
      const wrapper = createWrapper()
      const modal = wrapper.find('.modal')

      expect((modal.element as HTMLElement).style.display).toBe('none')

      await wrapper.setProps({ modelValue: true })
      expect((modal.element as HTMLElement).style.display).toBe('block')

      await wrapper.setProps({ modelValue: false })
      expect((modal.element as HTMLElement).style.display).toBe('none')
    })

    it('should show backdrop when open', async () => {
      const wrapper = createWrapper({ modelValue: true })
      await nextTick()

      const backdrop = wrapper.find('.modal-backdrop')
      expect(backdrop.exists()).toBe(true)
      expect(backdrop.classes()).toContain('show')
    })
  })

  describe('content display', () => {
    it('should display default text', () => {
      const wrapper = createWrapper({ modelValue: true })

      expect(wrapper.find('.modal-title').text()).toBe('Confirm')
      expect(wrapper.find('.modal-body p').text()).toBe('Are you sure?')
      expect(wrapper.find('.btn-secondary').text()).toBe('Cancel')
      expect(wrapper.findAll('button').filter(b => b.text() === 'Confirm')).toHaveLength(1)
    })

    it('should display custom text', () => {
      const wrapper = createWrapper({
        modelValue: true,
        title: 'Delete Item',
        message: 'This action cannot be undone.',
        confirmText: 'Delete',
        cancelText: 'Keep',
      })

      expect(wrapper.find('.modal-title').text()).toBe('Delete Item')
      expect(wrapper.find('.modal-body p').text()).toBe('This action cannot be undone.')
      expect(wrapper.find('.btn-secondary').text()).toBe('Keep')
      expect(wrapper.findAll('button').filter(b => b.text() === 'Delete')).toHaveLength(1)
    })

    it('should apply custom confirm button class', () => {
      const wrapper = createWrapper({
        modelValue: true,
        confirmClass: 'btn-danger',
      })

      const confirmButton = wrapper.findAll('button').filter(b => b.text() === 'Confirm')[0]
      expect(confirmButton.classes()).toContain('btn-danger')
    })
  })

  describe('user interactions', () => {
    it('should emit confirm event when confirm button clicked', async () => {
      const wrapper = createWrapper({ modelValue: true })

      const confirmButton = wrapper.findAll('button').filter(b => b.text() === 'Confirm')[0]
      await confirmButton.trigger('click')

      expect(wrapper.emitted('confirm')).toBeTruthy()
      expect(wrapper.emitted('confirm')).toHaveLength(1)
      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('should emit cancel event when cancel button clicked', async () => {
      const wrapper = createWrapper({ modelValue: true })

      const cancelButton = wrapper.find('.btn-secondary')
      await cancelButton.trigger('click')

      expect(wrapper.emitted('cancel')).toBeTruthy()
      expect(wrapper.emitted('cancel')).toHaveLength(1)
      expect(wrapper.emitted('update:modelValue')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('should emit cancel event when close button clicked', async () => {
      const wrapper = createWrapper({ modelValue: true })

      const closeButton = wrapper.find('.btn-close')
      await closeButton.trigger('click')

      expect(wrapper.emitted('cancel')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('should emit cancel event when clicking backdrop', async () => {
      const wrapper = createWrapper({ modelValue: true })

      const modal = wrapper.find('.modal')
      await modal.trigger('click')

      expect(wrapper.emitted('cancel')).toBeTruthy()
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('should not emit cancel when clicking modal content', async () => {
      const wrapper = createWrapper({ modelValue: true })

      const modalContent = wrapper.find('.modal-content')
      await modalContent.trigger('click')

      expect(wrapper.emitted('cancel')).toBeFalsy()
    })
  })

  describe('modal lifecycle', () => {
    it('should use custom modal id', () => {
      const wrapper = createWrapper({
        modelValue: true,
        modalId: 'deleteConfirmModal',
      })

      const modal = wrapper.find('.modal')
      expect(modal.attributes('id')).toBe('deleteConfirmModal')
      expect(modal.attributes('aria-labelledby')).toBe('deleteConfirmModalLabel')
    })

    it('should set correct aria attributes', async () => {
      const wrapper = createWrapper()
      const modal = wrapper.find('.modal')

      expect(modal.attributes('aria-hidden')).toBe('true')
      expect(modal.attributes('tabindex')).toBe('-1')

      await wrapper.setProps({ modelValue: true })
      expect(modal.attributes('aria-hidden')).toBe('false')
    })

    it('should clean up on unmount', () => {
      const wrapper = createWrapper()
      const disposeSpy = vi.fn()

      const modalInstance = (wrapper.vm as any).modalInstance
      if (modalInstance) {
        modalInstance.dispose = disposeSpy
      }

      wrapper.unmount()

      if (modalInstance) {
        expect(disposeSpy).toHaveBeenCalled()
      }
    })
  })
})
