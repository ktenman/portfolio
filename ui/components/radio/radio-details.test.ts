import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioDetails from './radio-details.vue'
import { recording } from './radio-fixture'
import type { RecordingSummary } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  radioService: { fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}` },
}))

describe('RadioDetails', () => {
  const render = (item: RecordingSummary = recording()) =>
    mount(RadioDetails, { props: { recording: item } })

  it('should fold the recording facts away', () => {
    expect(render().find('[data-testid="radio-recording"]').attributes('open')).toBeUndefined()
  })

  it('should note how often the stream dropped', () => {
    expect(render(recording({ drops: 2 })).text()).toContain(
      'Voog katkes 2 korda, midagi ei kadunud'
    )
  })

  it('should not note drops of a stream that never dropped', () => {
    expect(render().text()).not.toContain('Voog katkes')
  })

  it('should show the checksum of each recorded part', () => {
    expect(render().text()).toContain('SHA-256ab12')
  })
})
