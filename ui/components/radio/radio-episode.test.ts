import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { nextTick, ref } from 'vue'
import { createMemoryHistory, createRouter, createWebHistory, type Router } from 'vue-router'
import RadioEpisode from './radio-episode.vue'
import { recording } from './radio-fixture'
import { useRadioPlayer } from '../../composables/use-radio-player'
import type { RecordingSummary } from '../../models/generated/domain-models'

const { shown } = await vi.hoisted(async () => {
  const { shallowRef } = await import('vue')
  return { shown: shallowRef<RecordingSummary | null>(null) }
})

vi.mock('../../composables/use-radio-recordings', () => ({
  useRadioRecording: () => ({ recording: shown, isLoading: ref(false) }),
}))
vi.mock('../../services/api', () => ({
  radioService: {
    fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}`,
    position: () => Promise.resolve({ seconds: 0 }),
    savePosition: () => Promise.resolve(),
  },
}))

enableAutoUnmount(afterEach)

describe('RadioEpisode', () => {
  const player = useRadioPlayer()
  let audio: HTMLAudioElement

  beforeEach(() => {
    audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    player.attach(audio)
  })

  afterEach(() => player.close())

  const mountEpisode = (router: Router) =>
    mount(
      { template: '<router-view />' },
      {
        global: {
          plugins: [router],
          stubs: { RadioPlayer: { template: '<section><slot /></section>' }, RadioText: true },
        },
      }
    )

  const open = async (item: RecordingSummary, search = '') => {
    shown.value = item
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/radio/:name', component: RadioEpisode }],
    })
    await router.push(`/radio/${item.name}${search}`)
    return { router, wrapper: mountEpisode(router) }
  }

  it('should close a recording left without playing it', async () => {
    const { wrapper } = await open(recording())
    wrapper.unmount()
    expect(player.recording.value).toBeNull()
  })

  it('should keep a recording that has played when the page goes away', async () => {
    const { wrapper } = await open(recording())
    audio.dispatchEvent(new Event('play'))
    wrapper.unmount()
    expect(player.recording.value?.name).toBe('2026-10-17')
  })

  it('should open a recording on its cut version', async () => {
    await open(recording())
    expect(player.version.value).toBe('episode')
  })

  it('should open a recording without a cut version on the raw recording', async () => {
    await open(recording({ editList: null, episodeBytes: null }))
    expect(player.version.value).toBe('raw')
  })

  it('should show no view tabs', async () => {
    const { wrapper } = await open(recording())
    expect(wrapper.find('nav[aria-label="Show views"]').exists()).toBe(false)
  })

  const back = async (from: string) => {
    shown.value = recording()
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/radio', name: 'Radio', component: { template: '<p />' } },
        { path: '/radio/catch-up', name: 'RadioCatchUp', component: { template: '<p />' } },
        { path: '/radio/:name', component: RadioEpisode },
      ],
    })
    await router.push(from)
    await router.push(`/radio/${recording().name}`)
    const link = mountEpisode(router).get('a')
    return [link.attributes('aria-label'), link.attributes('href')]
  }

  it('should play a kept line from the search at its place in the cut version', async () => {
    await open(recording(), '?t=2300')
    expect([player.version.value, player.seconds.value]).toEqual(['episode', 1403.5])
  })

  it('should play a line cut from the episode on the raw recording', async () => {
    await open(recording(), '?t=2150')
    expect([player.version.value, player.seconds.value]).toEqual(['raw', 2150])
  })

  it('should play a searched line on the raw recording when the link asks for it', async () => {
    await open(recording(), '?t=2300&raw=1')
    expect([player.version.value, player.seconds.value]).toEqual(['raw', 2300])
  })

  it('should drop the searched line from the address once it plays', async () => {
    const { router } = await open(recording(), '?t=2150&raw=1')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/radio/2026-10-17')
  })

  it('should play a searched line on the raw recording when there is no cut version', async () => {
    await open(recording({ episodeBytes: null }), '?t=2300')
    expect([player.version.value, player.seconds.value]).toEqual(['raw', 2300])
  })

  it('should stay where the listener moved when the recording refreshes', async () => {
    await open(recording(), '?t=2300')
    player.seek(100)
    shown.value = recording()
    await nextTick()
    expect(player.seconds.value).toBe(100)
  })

  it('should go back to the day in Catch-up it was opened from', async () => {
    expect(await back('/radio/catch-up?day=2026-10-14')).toEqual([
      'Back to Catch-up',
      '/radio/catch-up?day=2026-10-14',
    ])
  })

  it('should go back to the search results it was opened from', async () => {
    expect(await back('/radio?q=valimised')).toEqual([
      'Back to search results',
      '/radio?q=valimised',
    ])
  })

  it('should go back to the recordings of the station it was opened from', async () => {
    expect(await back('/radio?station=aripaev')).toEqual([
      'Back to Recordings',
      '/radio?station=aripaev',
    ])
  })

  it('should go back to the day of the station it was opened from', async () => {
    expect(await back('/radio/catch-up?day=2026-10-14&station=aripaev')).toEqual([
      'Back to Catch-up',
      '/radio/catch-up?day=2026-10-14&station=aripaev',
    ])
  })

  it('should go back to the recordings when opened on its own', async () => {
    const link = (await open(recording())).wrapper.get('a')
    expect([link.attributes('aria-label'), link.attributes('href')]).toEqual([
      'Back to Recordings',
      '/radio',
    ])
  })

  it('should leave the name of the show to the player', async () => {
    const { wrapper } = await open(recording())
    expect(wrapper.find('h2').exists()).toBe(false)
  })

  it('should name the browser tab after the show and its day', async () => {
    await open(recording())
    expect(document.title).toBe('Keskpäevatund · Sat 17.10')
  })

  it('should show the text under the player', async () => {
    const { wrapper } = await open(recording())
    expect(wrapper.find('radio-text-stub').exists()).toBe(true)
  })

  it('should send old text links to the recording', async () => {
    const { default: app } = await import('../../router')
    await app.push('/radio/2026-10-17/text')
    expect(app.currentRoute.value.fullPath).toBe('/radio/2026-10-17')
  })

  it('should send old catch-up links to the catch-up page', async () => {
    const { default: app } = await import('../../router')
    await app.push('/radio/jarelkuulamine')
    expect(app.currentRoute.value.fullPath).toBe('/radio/catch-up')
  })

  it('should send old cuts links to the recording', async () => {
    const { default: app } = await import('../../router')
    await app.push('/radio/2026-10-17/cuts')
    expect(app.currentRoute.value.fullPath).toBe('/radio/2026-10-17')
  })
})
