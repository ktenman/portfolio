import { describe, it, expect, afterEach } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import ConfirmDialog from './confirm-dialog.vue'

enableAutoUnmount(afterEach)

const createWrapper = (props = {}) =>
  mount(ConfirmDialog, { props: { modelValue: false, ...props }, attachTo: document.body })

describe('ConfirmDialog', () => {
  describe('visibility', () => {
    it('stays closed when modelValue is false', () => {
      const wrapper = createWrapper()

      expect(wrapper.find('dialog').element.open).toBe(false)
    })

    it('opens the dialog when modelValue becomes true', async () => {
      const wrapper = createWrapper()

      await wrapper.setProps({ modelValue: true })

      expect(wrapper.find('dialog').element.open).toBe(true)
    })

    it('closes the dialog when modelValue becomes false', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper.setProps({ modelValue: false })

      expect(wrapper.find('dialog').element.open).toBe(false)
    })

    it('dont close on escape because the choice is mandatory', async () => {
      const wrapper = createWrapper({ modelValue: true })

      const event = new Event('cancel', { cancelable: true })
      wrapper.find('dialog').element.dispatchEvent(event)
      await wrapper.vm.$nextTick()

      expect(event.defaultPrevented).toBe(true)
    })
  })

  describe('content display', () => {
    it('should display default text', () => {
      const wrapper = createWrapper({ modelValue: true })

      expect(wrapper.find('.modal-title').text()).toBe('Confirm')
      expect(wrapper.find('.modal-body p').text()).toBe('Are you sure?')
      expect(wrapper.find('[data-testid="confirmDialogCancelButton"]').text()).toBe('Cancel')
      expect(wrapper.findAll('button').filter(b => b.text() === 'Confirm')).toHaveLength(1)
    })

    it('should display custom text', () => {
      const wrapper = createWrapper({
        modelValue: true,
        title: 'Kustuta üksus',
        message: 'Seda ei saa tagasi võtta.',
        confirmText: 'Kustuta',
        cancelText: 'Jäta alles',
      })

      expect(wrapper.find('.modal-title').text()).toBe('Kustuta üksus')
      expect(wrapper.find('.modal-body p').text()).toBe('Seda ei saa tagasi võtta.')
      expect(wrapper.find('[data-testid="confirmDialogCancelButton"]').text()).toBe('Jäta alles')
    })

    it('should apply custom confirm button class', () => {
      const wrapper = createWrapper({ modelValue: true, confirmClass: 'btn-danger' })

      const confirmButton = wrapper.findAll('button').filter(b => b.text() === 'Confirm')[0]

      expect(confirmButton.classes()).toContain('danger')
    })

    it('should use custom modal id', () => {
      const wrapper = createWrapper({ modelValue: true, modalId: 'deleteConfirmModal' })

      expect(wrapper.find('dialog').attributes('id')).toBe('deleteConfirmModal')
      expect(wrapper.find('dialog').attributes('aria-labelledby')).toBe('deleteConfirmModalLabel')
    })
  })

  describe('user interactions', () => {
    it('should emit confirm event when confirm button clicked', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper
        .findAll('button')
        .filter(b => b.text() === 'Confirm')[0]
        .trigger('click')

      expect(wrapper.emitted('confirm')).toHaveLength(1)
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('should emit cancel event when cancel button clicked', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper.find('[data-testid="confirmDialogCancelButton"]').trigger('click')

      expect(wrapper.emitted('cancel')).toHaveLength(1)
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('should emit cancel event when close button clicked', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper.find('.btn-close').trigger('click')

      expect(wrapper.emitted('cancel')).toHaveLength(1)
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('should emit cancel event when clicking backdrop', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper.find('dialog').trigger('click')

      expect(wrapper.emitted('cancel')).toHaveLength(1)
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('dont emit cancel when the parent closes the dialog after confirming', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper
        .findAll('button')
        .filter(b => b.text() === 'Confirm')[0]
        .trigger('click')
      await wrapper.setProps({ modelValue: false })

      expect(wrapper.emitted('cancel')).toBeFalsy()
    })

    it('should not emit cancel when clicking modal content', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper.find('.modal-content').trigger('click')

      expect(wrapper.emitted('cancel')).toBeFalsy()
    })
  })
})
