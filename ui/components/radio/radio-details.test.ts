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

  it('should leave the cuts out of the facts', async () => {
    const wrapper = render()
    await wrapper.get('button').trigger('click')
    expect(wrapper.text()).not.toContain('Lõiked')
  })

  const opened = async (item: RecordingSummary = recording()) => {
    const wrapper = render(item)
    await wrapper.get('button').trigger('click')
    return wrapper
  }

  it('should name the model that found the topics and breaks', async () => {
    expect((await opened()).text()).toContain('deepseek/deepseek-v4-flash')
  })

  it('should name the hosts', async () => {
    expect((await opened()).text()).toContain('Mari, Jüri')
  })

  it('should leave out how many reads agreed', async () => {
    expect((await opened()).text()).not.toContain('3 / 3')
  })

  it('should say the show is not analysed yet', async () => {
    expect((await opened(recording({ editList: null }))).text()).toContain(
      'Saadet pole veel analüüsitud.'
    )
  })

  it('should start folded', () => {
    expect(render().get('button').attributes('aria-expanded')).toBe('false')
  })
})
