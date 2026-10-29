import { describe, it, expect, afterEach, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { logoService } from '../../services/api'
import LogoReplacementModal from './logo-replacement-modal.vue'

enableAutoUnmount(afterEach)

describe('LogoReplacementModal', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('searches again when the search found no candidates', async () => {
    const candidates = vi.spyOn(logoService, 'getCandidates').mockResolvedValue([])
    const wrapper = mount(LogoReplacementModal, {
      props: { modelValue: false, holdingUuid: 'ühik-uuid', holdingName: 'Õunad AS' },
      attachTo: document.body,
    })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    await wrapper.get('[data-search-again]').trigger('click')

    expect(candidates).toHaveBeenCalledTimes(2)
  })
})
