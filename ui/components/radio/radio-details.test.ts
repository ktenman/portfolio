import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioDetails from './radio-details.vue'
import { recording } from './radio-fixture'
import type { RecordingSummary } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({
  radioService: { fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}` },
}))

describe('RadioDetails', () => {
  beforeEach(() => localStorage.clear())

  const render = (item: RecordingSummary = recording()) =>
    mount(RadioDetails, { props: { recording: item } })

  it('should leave out the recording panel', () => {
    expect(render().find('details').exists()).toBe(false)
  })

  it('should offer only the cleaned episode for download', () => {
    const links = render()
      .findAll('a[download]')
      .map(a => a.attributes('href'))
    expect(links).toEqual(['/api/radio/2026-10-17/episode.m4a'])
  })

  it('should keep the download reachable while folded', () => {
    const links = render()
      .findAll('a[download]')
      .map(a => a.attributes('aria-label'))
    expect(links).toEqual(['Laadi alla puhastatud, 34 MB'])
  })

  it('should start folded', () => {
    expect(render().get('button').attributes('aria-expanded')).toBe('false')
  })
})
