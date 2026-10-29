import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import InstrumentLogo from './instrument-logo.vue'

const UUID = '550e8400-e29b-41d4-a716-446655440000'
const OTHER_UUID = '6ba7b810-9dad-41d1-80b4-00c04fd430c8'

const mountLogo = (props: { logoUuid?: string | null; symbol?: string; version?: number | null }) =>
  mount(InstrumentLogo, { props: { symbol: 'VWCE:GER:EUR', size: 32, ...props } })

describe('InstrumentLogo', () => {
  it('shows the logo image when the instrument has one', () =>
    expect(mountLogo({ logoUuid: UUID }).find('img').attributes()).toMatchObject({
      src: `/api/logos/${UUID}`,
      alt: '',
      loading: 'lazy',
    }))

  it('asks for the logo again once it changes on the server', () =>
    expect(
      mountLogo({ logoUuid: UUID, version: 1793265300000 }).find('img').attributes('src')
    ).toBe(`/api/logos/${UUID}?v=1793265300000`))

  it('shows the monogram when the instrument has no logo', () =>
    expect(mountLogo({ logoUuid: null, symbol: 'ühik:TLN:EUR' }).text()).toBe('Ü'))

  it('shows the monogram when the image fails to load', async () => {
    const wrapper = mountLogo({ logoUuid: UUID, symbol: 'TSTINF:GER:EUR' })
    await wrapper.find('img').trigger('error')
    expect([wrapper.find('img').exists(), wrapper.text()]).toEqual([false, 'T'])
  })

  it('tries the image again for a different logo after a failure', async () => {
    const wrapper = mountLogo({ logoUuid: UUID })
    await wrapper.find('img').trigger('error')
    await wrapper.setProps({ logoUuid: OTHER_UUID })
    expect(wrapper.find('img').attributes('src')).toBe(`/api/logos/${OTHER_UUID}`)
  })

  it('tries the image again for a new version after a failure', async () => {
    const wrapper = mountLogo({ logoUuid: UUID })
    await wrapper.find('img').trigger('error')
    await wrapper.setProps({ version: 1793265300000 })
    expect(wrapper.find('img').attributes('src')).toBe(`/api/logos/${UUID}?v=1793265300000`)
  })
})
