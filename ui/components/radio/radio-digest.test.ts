import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioDigest from './radio-digest.vue'
import { digest } from './radio-digest-fixture'

const player = vi.hoisted(() => ({
  load: vi.fn(),
  playFrom: vi.fn(),
  toggle: vi.fn(),
  playing: { value: false },
  recording: { value: null as { name: string } | null },
}))

vi.mock('../../services/api', () => ({ radioService: { digests: vi.fn() } }))
vi.mock('../../composables/use-radio-player', () => ({ useRadioPlayer: () => player }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const mount = async (digests = [digest()]) => {
  vi.mocked(radioService.digests).mockResolvedValue(digests)
  const wrapper = renderWithProviders(RadioDigest)
  await flushPromises()
  return wrapper
}

describe('RadioDigest', () => {
  it('should show the first four stories and how many more', async () => {
    const wrapper = await mount()
    expect([
      wrapper.findAll('[data-testid="radio-digest-story"]').length,
      wrapper.get('[data-testid="radio-digest-more"]').text(),
    ]).toEqual([4, '2 more stories'])
  })

  it('should name the show each story was taken from and how many others told it', async () => {
    const story = (await mount()).findAll('[data-testid="radio-digest-story"]')[2]!
    expect(story.text()).toBe('3Telia koondamised ja tehisaruDigitund +1')
  })

  it('should say when it was built, how many stories it tells and for how long', async () => {
    expect((await mount()).get('[data-testid="radio-digest-meta"]').text()).toBe(
      'Mon 16.11 · 12:00 · 6 stories · 20 min'
    )
  })

  it('should link to the digest', async () => {
    const link = (await mount()).get('a')
    expect(link.attributes('href')).toBe('/radio/week-in-radio-2026-11-16')
  })

  it('should play the digest from its play button', async () => {
    const wrapper = await mount()
    await wrapper.get('button[aria-label="Play: Week in Radio 09.11–15.11.2026"]').trigger('click')
    expect(player.load).toHaveBeenCalledWith(digest().recording, 'episode')
  })

  it('should resume the digest where it was left', async () => {
    vi.clearAllMocks()
    const wrapper = await mount()
    await wrapper.get('button[aria-label="Play: Week in Radio 09.11–15.11.2026"]').trigger('click')
    expect([player.playFrom.mock.calls.length, player.toggle.mock.calls.length]).toEqual([0, 1])
  })

  it('should hide the card when there is no digest', async () => {
    expect((await mount([])).find('[data-testid="radio-digest"]').exists()).toBe(false)
  })
})
