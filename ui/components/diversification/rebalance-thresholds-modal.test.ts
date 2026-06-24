import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import RebalanceThresholdsModal from './rebalance-thresholds-modal.vue'

const initial = {
  driftingThresholdRel: 10,
  rebalanceThresholdRel: 25,
  rebalanceThresholdAbs: 5,
}

describe('RebalanceThresholdsModal', () => {
  it('renders three numeric inputs pre-filled with current values', () => {
    const wrapper = mount(RebalanceThresholdsModal, {
      props: { modelValue: initial, open: true },
    })
    const inputs = wrapper.findAll('input[type="number"]')

    expect(inputs).toHaveLength(3)
    expect((inputs[0].element as HTMLInputElement).value).toBe('10')
    expect((inputs[1].element as HTMLInputElement).value).toBe('25')
    expect((inputs[2].element as HTMLInputElement).value).toBe('5')
  })

  it('disables Save when driftingThresholdRel exceeds rebalanceThresholdRel', async () => {
    const wrapper = mount(RebalanceThresholdsModal, {
      props: { modelValue: initial, open: true },
    })
    const driftingInput = wrapper.findAll('input[type="number"]')[0]
    await driftingInput.setValue('30')

    expect(wrapper.find('button.save-btn').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Drifting threshold must be less than or equal')
  })

  it('disables Save when any input is negative', async () => {
    const wrapper = mount(RebalanceThresholdsModal, {
      props: { modelValue: initial, open: true },
    })
    await wrapper.findAll('input[type="number"]')[2].setValue('-1')

    expect(wrapper.find('button.save-btn').attributes('disabled')).toBeDefined()
  })

  it('disables Save when any input is empty (NaN)', async () => {
    const wrapper = mount(RebalanceThresholdsModal, {
      props: { modelValue: initial, open: true },
    })
    await wrapper.findAll('input[type="number"]')[0].setValue('')

    expect(wrapper.find('button.save-btn').attributes('disabled')).toBeDefined()
  })

  it('emits save with updated values when Save clicked', async () => {
    const wrapper = mount(RebalanceThresholdsModal, {
      props: { modelValue: initial, open: true },
    })
    await wrapper.findAll('input[type="number"]')[0].setValue('7.5')
    await wrapper.findAll('input[type="number"]')[1].setValue('20')
    await wrapper.find('button.save-btn').trigger('click')

    expect(wrapper.emitted('save')).toHaveLength(1)
    expect(wrapper.emitted('save')![0][0]).toEqual({
      driftingThresholdRel: 7.5,
      rebalanceThresholdRel: 20,
      rebalanceThresholdAbs: 5,
    })
  })

  it('resets to defaults when Reset clicked', async () => {
    const wrapper = mount(RebalanceThresholdsModal, {
      props: {
        modelValue: {
          driftingThresholdRel: 7.5,
          rebalanceThresholdRel: 20,
          rebalanceThresholdAbs: 3,
        },
        open: true,
      },
    })
    await wrapper.find('button.reset-btn').trigger('click')
    await nextTick()

    const inputs = wrapper.findAll('input[type="number"]')
    expect((inputs[0].element as HTMLInputElement).value).toBe('10')
    expect((inputs[1].element as HTMLInputElement).value).toBe('25')
    expect((inputs[2].element as HTMLInputElement).value).toBe('5')
  })

  it('emits update:open false on Cancel without emitting save', async () => {
    const wrapper = mount(RebalanceThresholdsModal, {
      props: { modelValue: initial, open: true },
    })
    await wrapper.find('button.cancel-btn').trigger('click')

    expect(wrapper.emitted('update:open')![0]).toEqual([false])
    expect(wrapper.emitted('save')).toBeUndefined()
  })
})
