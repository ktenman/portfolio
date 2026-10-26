import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import RadioDetails from './radio-details.vue'
import { recording } from './radio-fixture'
import { type RecordingSummary, TranscriptState } from '../../models/generated/domain-models'

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
    expect(links).toEqual(['Download the clean version, 34 MB'])
  })

  it('should leave the cuts out of the facts', async () => {
    const wrapper = render()
    await wrapper.get('button').trigger('click')
    expect(wrapper.text()).not.toContain('Cuts')
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
      'The show has not been analyzed yet.'
    )
  })

  const rows = async (item: RecordingSummary = recording()) =>
    (await opened(item))
      .findAll('dl > div')
      .map(row => [row.get('dt').text(), row.get('dd').text()])

  it('should list the analysis, the hosts and the models behind the speech and sounds', async () => {
    expect((await rows()).map(([label]) => label)).toEqual([
      'Analysis',
      'Hosts',
      'Speech',
      'Sounds',
    ])
  })

  it('should name the speech model by its revision', async () => {
    expect(await rows()).toContainEqual(['Speech', 'Zipformer · 2812262'])
  })

  it('should name the sounds model by its revision', async () => {
    expect(await rows()).toContainEqual(['Sounds', 'CED-tiny · 2024-04-19'])
  })

  it('should give the full speech model and revision on hover', async () => {
    const wrapper = await opened()
    const speech = wrapper.findAll('dl > div').find(row => row.get('dt').text() === 'Speech')!
    expect(speech.get('dd').attributes('title')).toBe(
      'TalTechNLP/streaming-zipformer-large.et-en at 2812262bd4fd5d4327dc09ca0e6c19e8a32a03d6'
    )
  })

  it('should show a dash for a model recorded before revisions were kept', async () => {
    expect(await rows(recording({ speech: null }))).toContainEqual(['Speech', '—'])
  })

  it('should say the models are still at work while the show records', async () => {
    const item = recording({
      speech: null,
      sounds: null,
      transcriptState: TranscriptState.RECORDING,
    })
    expect(await rows(item)).toContainEqual(['Sounds', 'In progress'])
  })

  it('should list only the models of a show not analyzed yet', async () => {
    expect((await rows(recording({ editList: null }))).map(([label]) => label)).toEqual([
      'Speech',
      'Sounds',
    ])
  })

  it('should start folded', () => {
    expect(render().get('button').attributes('aria-expanded')).toBe('false')
  })
})
