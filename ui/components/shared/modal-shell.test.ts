import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ModalShell from './modal-shell.vue'

const mountShell = (props = {}) =>
  mount(ModalShell, {
    props: { open: false, modalId: 'testModal', title: 'Tähtis päring', ...props },
    attachTo: document.body,
  })

describe('ModalShell', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    document.body.style.overflow = ''
    document.body.style.paddingRight = ''
  })

  it('stays closed when open is false', () => {
    const wrapper = mountShell()

    expect(wrapper.find('dialog').element.open).toBe(false)
  })

  it('opens the native dialog when open becomes true', async () => {
    const wrapper = mountShell()

    await wrapper.setProps({ open: true })

    expect(wrapper.find('dialog').element.open).toBe(true)
  })

  it('opens the native dialog when mounted already open', () => {
    const wrapper = mountShell({ open: true })

    expect(wrapper.find('dialog').element.open).toBe(true)
  })

  it('marks the content as the autofocus target so focus skips the close button', () => {
    const wrapper = mountShell({ open: true })

    expect(wrapper.find('.modal-content').attributes('autofocus')).toBeDefined()
  })

  it('closes the native dialog when open becomes false', async () => {
    const wrapper = mountShell({ open: true })

    await wrapper.setProps({ open: false })

    expect(wrapper.find('dialog').element.open).toBe(false)
  })

  it('emits update:open false when the dialog closes natively', async () => {
    const wrapper = mountShell({ open: true })

    wrapper.find('dialog').element.close()
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
  })

  it('dont emit update:open when the parent already closed the dialog', async () => {
    const wrapper = mountShell({ open: true })

    await wrapper.setProps({ open: false })

    expect(wrapper.emitted('update:open')).toBeFalsy()
  })

  it('closes when the close button is clicked', async () => {
    const wrapper = mountShell({ open: true })

    await wrapper.find('.btn-close').trigger('click')

    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
  })

  it('closes when the backdrop is clicked', async () => {
    const wrapper = mountShell({ open: true })

    await wrapper.find('dialog').trigger('click')

    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
  })

  it('dont close when the dialog content is clicked', async () => {
    const wrapper = mountShell({ open: true })

    await wrapper.find('.modal-content').trigger('click')

    expect(wrapper.emitted('update:open')).toBeFalsy()
  })

  it('suppresses escape when closeOnEsc is false', async () => {
    const wrapper = mountShell({ open: true, closeOnEsc: false })

    const event = new Event('cancel', { cancelable: true })
    wrapper.find('dialog').element.dispatchEvent(event)
    await wrapper.vm.$nextTick()

    expect(event.defaultPrevented).toBe(true)
  })

  it('allows escape when closeOnEsc is true', async () => {
    const wrapper = mountShell({ open: true })

    const event = new Event('cancel', { cancelable: true })
    wrapper.find('dialog').element.dispatchEvent(event)
    await wrapper.vm.$nextTick()

    expect(event.defaultPrevented).toBe(false)
  })

  it('locks body scroll while open', async () => {
    const wrapper = mountShell()

    await wrapper.setProps({ open: true })

    expect(document.body.style.overflow).toBe('hidden')
  })

  it('restores body scroll when closed', async () => {
    const wrapper = mountShell({ open: true })

    await wrapper.setProps({ open: false })

    expect(document.body.style.overflow).toBe('')
  })

  it('restores body scroll when unmounted while open', () => {
    const wrapper = mountShell({ open: true })

    wrapper.unmount()

    expect(document.body.style.overflow).toBe('')
  })

  it('renders the title into the modal title element', () => {
    const wrapper = mountShell({ open: true })

    expect(wrapper.find('.modal-title').text()).toBe('Tähtis päring')
  })

  it('links the title to the dialog for screen readers', () => {
    const wrapper = mountShell({ open: true })

    expect(wrapper.find('dialog').attributes('aria-labelledby')).toBe('testModalLabel')
    expect(wrapper.find('.modal-title').attributes('id')).toBe('testModalLabel')
  })

  it('renders the default slot into the modal body', () => {
    const wrapper = mount(ModalShell, {
      props: { open: true, modalId: 'testModal' },
      slots: { default: '<p class="probe">Sisu</p>' },
      attachTo: document.body,
    })

    expect(wrapper.find('.modal-body .probe').text()).toBe('Sisu')
  })

  it('renders the footer slot into the modal footer', () => {
    const wrapper = mount(ModalShell, {
      props: { open: true, modalId: 'testModal' },
      slots: { footer: '<button class="probe">Sulge</button>' },
      attachTo: document.body,
    })

    expect(wrapper.find('.modal-footer .probe').text()).toBe('Sulge')
  })

  it('applies the large size class to the dialog wrapper', () => {
    const wrapper = mountShell({ open: true, size: 'lg' })

    expect(wrapper.find('.modal-dialog').classes()).toContain('modal-lg')
  })

  it('applies the centered class to the dialog wrapper', () => {
    const wrapper = mountShell({ open: true, centered: true })

    expect(wrapper.find('.modal-dialog').classes()).toContain('modal-dialog-centered')
  })

  it('dont apply the fade class that would zero the opacity', () => {
    const wrapper = mountShell({ open: true })

    expect(wrapper.find('dialog').classes()).not.toContain('fade')
  })
})
