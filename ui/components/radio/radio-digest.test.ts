import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h } from 'vue'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { DigestShow } from '../../models/generated/domain-models'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioDigest from './radio-digest.vue'
import { dayDigest, digest } from './radio-digest-fixture'

const player = vi.hoisted(() => ({
  load: vi.fn(),
  playFrom: vi.fn(),
  toggle: vi.fn(),
  playing: { value: false },
  progress: { value: 0 },
  recording: { value: null as { name: string } | null },
}))

vi.mock('../../services/api', () => ({
  radioService: {
    digests: vi.fn(),
    digestBuild: vi.fn().mockResolvedValue(null),
  },
}))
vi.mock('../../composables/use-confirm', () => ({ useConfirm: () => ({ confirm: vi.fn() }) }))
vi.mock('../../composables/use-radio-player', () => ({ useRadioPlayer: () => player }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const mount = async (digests = [digest()], show = DigestShow.WEEK) => {
  vi.mocked(radioService.digests).mockResolvedValue(digests)
  const wrapper = renderWithProviders({ render: () => h(RadioDigest, { show }) })
  await flushPromises()
  return wrapper
}

const load = () => (player.recording.value = { name: digest().recording.name })

const arc = (wrapper: ReturnType<typeof renderWithProviders>) =>
  wrapper.get('[data-testid="radio-ring"] rect:last-child')

const unfold = async (digests = [digest()]) => {
  const wrapper = await mount(digests)
  await wrapper.get('button[aria-expanded]').trigger('click')
  return wrapper
}

const play = async () => {
  const wrapper = await mount()
  await wrapper.get('button[aria-label="Play: Week in Radio 09.11–15.11.2026"]').trigger('click')
}

describe('RadioDigest', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    player.recording.value = null
    player.playing.value = false
    player.progress.value = 0
  })

  it('should show the newest digest of its own show', async () => {
    expect((await mount([digest(), dayDigest()], DigestShow.DAY)).get('h3').text()).toBe(
      'Day in Radio'
    )
  })

  it('should show nothing before its show has a digest', async () => {
    const wrapper = await mount([digest()], DigestShow.DAY)
    expect(wrapper.find('[data-testid="radio-digest"]').exists()).toBe(false)
  })

  it('should keep the stories folded away until they are asked for', async () => {
    expect((await mount()).findAll('[data-testid="radio-digest-story"]')).toHaveLength(0)
  })

  it('should list every story once unfolded', async () => {
    expect((await unfold()).findAll('[data-testid="radio-digest-story"]')).toHaveLength(6)
  })

  it('should fold the stories away again', async () => {
    const wrapper = await unfold()
    await wrapper
      .get('button[aria-label="Stories: Week in Radio 09.11–15.11.2026"]')
      .trigger('click')
    expect(wrapper.findAll('[data-testid="radio-digest-story"]')).toHaveLength(0)
  })

  it('should stay unfolded on the next visit', async () => {
    ;(await unfold()).unmount()
    expect((await mount()).findAll('[data-testid="radio-digest-story"]')).toHaveLength(6)
  })

  it('should fold each show on its own', async () => {
    ;(await unfold()).unmount()
    const day = await mount([digest(), dayDigest()], DigestShow.DAY)
    expect(day.findAll('[data-testid="radio-digest-story"]')).toHaveLength(0)
  })

  it('should offer no rebuild while the stories are folded away', async () => {
    expect((await mount()).find('[data-testid="radio-digest-rebuild"]').exists()).toBe(false)
  })

  it('should offer to rebuild the week under its stories', async () => {
    expect((await unfold()).get('[data-testid="radio-digest-rebuild"]').text()).toBe(
      'Rebuild from the last 7 days'
    )
  })

  it('should name the show each story was taken from and how many others told it', async () => {
    const story = (await unfold()).findAll('[data-testid="radio-digest-story"]')[2]!
    expect(story.text()).toBe('3Telia koondamised ja tehisaruDigitund +1')
  })

  it('should say when it was built, how many stories it tells and for how long', async () => {
    expect((await mount()).get('[data-testid="radio-digest-meta"]').text()).toBe(
      'Mon 16.11\u00a0· 12:00\u00a0· 6 stories\u00a0· 20 min'
    )
  })

  it('should link to the digest', async () => {
    const link = (await mount()).get('a')
    expect(link.attributes('href')).toBe('/radio/week-in-radio-2026-11-16')
  })

  it('should play the digest from its play button', async () => {
    await play()
    expect(player.load).toHaveBeenCalledWith(digest().recording, 'episode')
  })

  it('should resume the digest where it was left', async () => {
    await play()
    expect([player.playFrom.mock.calls.length, player.toggle.mock.calls.length]).toEqual([0, 1])
  })

  it.each([
    [0.25, '25 75'],
    [0.75, '75 25'],
    [1, undefined],
  ])('should draw the arc of a digest heard to %s', async (share, dash) => {
    load()
    player.playing.value = true
    player.progress.value = share
    expect(arc(await mount()).attributes('stroke-dasharray')).toBe(dash)
  })

  it('should draw no arc on a digest not yet heard', async () => {
    load()
    expect(arc(await mount()).classes()).toContain('invisible')
  })

  it("should draw the ring of a digest that plays as a rounded square from 12 o'clock", async () => {
    load()
    player.playing.value = true
    player.progress.value = 0.5
    expect(arc(await mount()).classes()).toContain('[stroke-dashoffset:-5.2]')
  })

  it('should draw the ring of a paused digest as a circle', async () => {
    load()
    player.progress.value = 0.5
    expect(arc(await mount()).classes()).toContain('[rx:50%]')
  })

  it('should draw no ring around a digest that is not in the player', async () => {
    expect((await mount()).find('[data-testid="radio-ring"]').exists()).toBe(false)
  })

  it('should keep the ring and the play glyph on a paused digest', async () => {
    load()
    const wrapper = await mount()
    expect([
      wrapper.find('[data-testid="radio-ring"]').exists(),
      wrapper.find('button[aria-label="Play: Week in Radio 09.11–15.11.2026"]').exists(),
    ]).toEqual([true, true])
  })

  it('should keep the digest in the player off solid brass', async () => {
    load()
    player.playing.value = true
    const classes = (await mount()).get('button').classes()
    expect([classes.includes('bg-surface'), classes.includes('bg-brass')]).toEqual([true, false])
  })

  it('should spring a digest that plays into a rounded square', async () => {
    load()
    player.playing.value = true
    expect((await mount()).get('button').classes()).toEqual(
      expect.arrayContaining(['rounded-[33%]', '[--morph:500ms_var(--ease-spring)]'])
    )
  })

  it('should settle a paused digest in the player back into a circle', async () => {
    load()
    expect((await mount()).get('button').classes()).toEqual(
      expect.arrayContaining(['rounded-[50%]', '[--morph:380ms_var(--ease-settle)]'])
    )
  })

  it('should hide the ring from screen readers', async () => {
    load()
    expect((await mount()).get('[data-testid="radio-ring"]').attributes('aria-hidden')).toBe('true')
  })

  it('should hide the card when there is no digest', async () => {
    expect((await mount([])).find('[data-testid="radio-digest"]').exists()).toBe(false)
  })
})
