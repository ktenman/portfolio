import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { type DigestBuild, DigestStep } from '../../models/generated/domain-models'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioDigestBuild from './radio-digest-build.vue'
import { digestBuild, digestFailure } from './radio-digest-fixture'

vi.mock('../../services/api', () => ({ radioService: { digestBuild: vi.fn() } }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const mount = async (found: DigestBuild | null = digestBuild()) => {
  vi.mocked(radioService.digestBuild).mockResolvedValue(found)
  const wrapper = renderWithProviders(RadioDigestBuild)
  await flushPromises()
  return wrapper
}

const rows = async (found: DigestBuild) =>
  (await mount(found)).findAll('[data-testid="radio-digest-build-story"]')

const gray = async (found: DigestBuild) =>
  (await rows(found)).map(row => row.classes().includes('text-ink-soft'))

describe('RadioDigestBuild', () => {
  it('should show nothing while no digest builds', async () => {
    expect((await mount(null)).find('[data-testid="radio-digest-build"]').exists()).toBe(false)
  })

  it('should name the show it builds on the progress bar', async () => {
    const found = digestBuild({ title: 'Day in Radio 16.11.2026' })
    const bar = (await mount(found)).get('[role="progressbar"]')
    expect(bar.attributes('aria-label')).toBe('Day in Radio 16.11.2026')
  })

  it('should show the title the build is given', async () => {
    const found = digestBuild({ title: 'Week in Radio 12.10–18.10.2026' })
    expect((await mount(found)).get('h3').text()).toBe('Week in Radio 12.10–18.10.2026')
  })

  it('should hold four places while the stories are chosen', async () => {
    const found = digestBuild({ step: DigestStep.STORIES, titles: [], done: 0 })
    expect((await rows(found)).map(row => row.text())).toEqual(['1', '2', '3', '4'])
  })

  it('should list the picked stories in gray while their clips are chosen', async () => {
    expect(await gray(digestBuild({ step: DigestStep.CLIPS }))).toEqual([true, true, true, true])
  })

  it('should darken each story once its clip is cut', async () => {
    expect(await gray(digestBuild())).toEqual([false, true, true, true])
  })

  it('should darken every story while mixing', async () => {
    expect(await gray(digestBuild({ step: DigestStep.MIXING, done: 45 }))).toEqual([
      false,
      false,
      false,
      false,
    ])
  })

  it('should name the step and how many clips are cut', async () => {
    const wrapper = await mount(digestBuild({ done: 2 }))
    expect(wrapper.get('[data-testid="radio-digest-build-step"]').text()).toBe(
      'Cutting the clips · 2 of 6'
    )
  })

  it('should name how far the mix is', async () => {
    const wrapper = await mount(digestBuild({ step: DigestStep.MIXING, done: 45 }))
    expect(wrapper.get('[data-testid="radio-digest-build-step"]').text()).toBe('Mixing · 45%')
  })

  it('should show the first four stories and how many more', async () => {
    const wrapper = await mount(digestBuild({ step: DigestStep.CLIPS }))
    expect([
      wrapper.findAll('[data-testid="radio-digest-build-story"]').length,
      wrapper.get('[data-testid="radio-digest-build-more"]').text(),
    ]).toEqual([4, '2 more stories'])
  })

  it('should give the meter its step and time left', async () => {
    const wrapper = await mount(digestBuild({ done: 2 }))
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuetext')).toBe(
      '58%, Cutting the clips · 2 of 6, ~5 min left'
    )
  })

  it('should give the reason and the next try of a failed digest', async () => {
    const wrapper = await mount(digestBuild({ step: null, titles: [], failure: digestFailure() }))
    expect(wrapper.get('[data-testid="radio-digest-build-failure"]').text()).toBe(
      'Could not make the digest: The model gave no judgement · Next try at 01:00 (2 of 3)'
    )
  })

  it('should say it stopped for the day after three tries', async () => {
    const failure = digestFailure({ tries: 3, nextTry: null })
    const wrapper = await mount(digestBuild({ step: null, titles: [], failure }))
    expect(wrapper.get('[data-testid="radio-digest-build-failure"]').text()).toContain(
      'Stopped for today after 3 tries'
    )
  })

  it('should name a single try', async () => {
    const failure = digestFailure({ tries: 1, nextTry: null })
    const wrapper = await mount(digestBuild({ step: null, titles: [], failure }))
    expect(wrapper.get('[data-testid="radio-digest-build-failure"]').text()).toContain(
      'Stopped for today after 1 try'
    )
  })
})
