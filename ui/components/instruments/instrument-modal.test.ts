import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import InstrumentModal from './instrument-modal.vue'
import InstrumentForm from './instrument-form.vue'
import { ProviderName } from '../../models/generated/domain-models'
import { createInstrumentDto } from '../../tests/fixtures'

vi.mock('./instrument-form.vue', () => ({
  default: {
    name: 'InstrumentForm',
    props: ['initialData'],
    emits: ['submit'],
    template:
      '<form id="instrumentForm" @submit.prevent="$emit(\'submit\', formData)"><slot /></form>',
    setup() {
      const formData = { symbol: 'TEST', name: 'Test InstrumentDto' }
      return { formData }
    },
  },
}))

describe('InstrumentModal', () => {
  const mockInstrument = createInstrumentDto({
    id: 1,
    symbol: 'AAPL',
    name: 'Apple Inc.',
    category: 'STOCK',
    providerName: ProviderName.FT,
  })

  const createWrapper = (props = {}) =>
    mount(InstrumentModal, {
      props: { open: false, ...props },
      attachTo: document.body,
    })

  describe('visibility', () => {
    it('stays closed when open is false', () => {
      const wrapper = createWrapper()

      expect(wrapper.find('dialog').element.open).toBe(false)
    })

    it('opens the dialog when open becomes true', async () => {
      const wrapper = createWrapper()

      await wrapper.setProps({ open: true })

      expect(wrapper.find('dialog').element.open).toBe(true)
    })

    it('closes on escape', async () => {
      const wrapper = createWrapper({ open: true })

      const event = new Event('cancel', { cancelable: true })
      wrapper.find('dialog').element.dispatchEvent(event)
      await wrapper.vm.$nextTick()

      expect(event.defaultPrevented).toBe(false)
    })
  })

  describe('component rendering', () => {
    it('should keep the modal id the harness targets', () => {
      const wrapper = createWrapper({ open: true })

      expect(wrapper.find('dialog').attributes('id')).toBe('instrumentModal')
      expect(wrapper.find('.modal-title').attributes('id')).toBe('instrumentModalLabel')
    })

    it('should display "Add New Instrument" title when creating new', () => {
      const wrapper = createWrapper({ open: true })

      expect(wrapper.find('.modal-title').text()).toBe('Add New Instrument')
    })

    it('should display "Edit Instrument" title when editing', () => {
      const wrapper = createWrapper({ open: true, instrument: mockInstrument })

      expect(wrapper.find('.modal-title').text()).toBe('Edit Instrument')
    })

    it('should show "Save Instrument" button when creating new', () => {
      const wrapper = createWrapper({ open: true })

      expect(wrapper.find('.btn-primary').text()).toBe('Save Instrument')
    })

    it('should show "Update Instrument" button when editing', () => {
      const wrapper = createWrapper({ open: true, instrument: mockInstrument })

      expect(wrapper.find('.btn-primary').text()).toBe('Update Instrument')
    })
  })

  describe('props handling', () => {
    it('should pass empty object as default instrument', () => {
      const wrapper = createWrapper({ open: true })
      const form = wrapper.findComponent(InstrumentForm)

      expect(form.props('initialData')).toEqual({})
    })

    it('should pass instrument prop to form', () => {
      const wrapper = createWrapper({ open: true, instrument: mockInstrument })
      const form = wrapper.findComponent(InstrumentForm)

      expect(form.props('initialData')).toEqual(mockInstrument)
    })
  })

  describe('event handling', () => {
    it('should emit save event when form submits', async () => {
      const wrapper = createWrapper({ open: true })
      const formData = { symbol: 'NEW', name: 'New InstrumentDto' }

      await wrapper.findComponent(InstrumentForm).vm.$emit('submit', formData)

      expect(wrapper.emitted('save')?.[0]).toEqual([formData])
    })

    it('should emit save event with editing data', async () => {
      const wrapper = createWrapper({ open: true, instrument: mockInstrument })
      const updatedData = { ...mockInstrument, currentPrice: 150.25 }

      await wrapper.findComponent(InstrumentForm).vm.$emit('submit', updatedData)

      expect(wrapper.emitted('save')?.[0]).toEqual([updatedData])
    })

    it('emits update:open false when the cancel button is clicked', async () => {
      const wrapper = createWrapper({ open: true })

      await wrapper
        .findAll('button')
        .filter(b => b.text() === 'Cancel')[0]
        .trigger('click')

      expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
    })

    it('emits update:open false when the close button is clicked', async () => {
      const wrapper = createWrapper({ open: true })

      await wrapper.find('.btn-close').trigger('click')

      expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
    })
  })
})
