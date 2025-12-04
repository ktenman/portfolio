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

  const createWrapper = (props = {}) => {
    return mount(InstrumentModal, {
      props: {
        modalId: 'testModal',
        ...props,
      },
      global: {
        stubs: {
          InstrumentForm: true,
        },
      },
    })
  }

  describe('component rendering', () => {
    it('should set correct modal id', () => {
      const wrapper = createWrapper({ modalId: 'customModal' })

      expect(wrapper.find('.modal').attributes('id')).toBe('customModal')
      expect(wrapper.find('.modal-title').attributes('id')).toBe('customModalLabel')
    })

    it('should display "Add New Instrument" title when creating new', () => {
      const wrapper = createWrapper()

      expect(wrapper.find('.modal-title').text()).toBe('Add New Instrument')
    })

    it('should display "Edit Instrument" title when editing', () => {
      const wrapper = createWrapper({ instrument: mockInstrument })

      expect(wrapper.find('.modal-title').text()).toBe('Edit Instrument')
    })

    it('should show "Save Instrument" button when creating new', () => {
      const wrapper = createWrapper()

      const submitButton = wrapper.find('.btn-primary')
      expect(submitButton.text()).toBe('Save Instrument')
    })

    it('should show "Update Instrument" button when editing', () => {
      const wrapper = createWrapper({ instrument: mockInstrument })

      const submitButton = wrapper.find('.btn-primary')
      expect(submitButton.text()).toBe('Update Instrument')
    })
  })

  describe('props handling', () => {
    it('should use default modalId when not provided', () => {
      const wrapper = mount(InstrumentModal, {
        global: {
          stubs: {
            InstrumentForm: true,
          },
        },
      })

      expect(wrapper.find('.modal').attributes('id')).toBe('instrumentModal')
    })

    it('should pass empty object as default instrument', () => {
      const wrapper = createWrapper()
      const form = wrapper.findComponent(InstrumentForm)

      expect(form.props('initialData')).toEqual({})
    })

    it('should pass instrument prop to form', () => {
      const wrapper = createWrapper({ instrument: mockInstrument })
      const form = wrapper.findComponent(InstrumentForm)

      expect(form.props('initialData')).toEqual(mockInstrument)
    })
  })

  describe('event handling', () => {
    it('should emit save event when form submits', async () => {
      const wrapper = mount(InstrumentModal, {
        props: {
          modalId: 'testModal',
        },
      })

      const formData = { symbol: 'NEW', name: 'New InstrumentDto' }
      const form = wrapper.findComponent(InstrumentForm)

      await form.vm.$emit('submit', formData)

      expect(wrapper.emitted('save')).toBeTruthy()
      expect(wrapper.emitted('save')?.[0]).toEqual([formData])
    })

    it('should emit save event with editing data', async () => {
      const wrapper = mount(InstrumentModal, {
        props: {
          modalId: 'testModal',
          instrument: mockInstrument,
        },
      })

      const updatedData = { ...mockInstrument, currentPrice: 150.25 }
      const form = wrapper.findComponent(InstrumentForm)

      await form.vm.$emit('submit', updatedData)

      expect(wrapper.emitted('save')).toBeTruthy()
      expect(wrapper.emitted('save')?.[0]).toEqual([updatedData])
    })
  })
})
