import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { createMemoryHistory, createRouter, createWebHistory } from 'vue-router'
import RadioEpisode from './radio-episode.vue'
import { recording } from './radio-fixture'
import { useRadioPlayer } from '../../composables/use-radio-player'
import type { RecordingSummary } from '../../models/generated/domain-models'

const { shown } = vi.hoisted(() => ({ shown: { value: null as RecordingSummary | null } }))

vi.mock('../../composables/use-radio-recordings', () => ({
  useRadioRecording: () => ({ recording: ref(shown.value), isLoading: ref(false) }),
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

  beforeEach(() => {
    const audio = document.createElement('audio')
    vi.spyOn(audio, 'play').mockResolvedValue()
    player.attach(audio)
  })

  afterEach(() => player.detach())

  const open = async (item: RecordingSummary) => {
    shown.value = item
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/radio/:name', component: RadioEpisode }],
    })
    await router.push(`/radio/${item.name}`)
    const wrapper = mount(
      { template: '<router-view />' },
      {
        global: {
          plugins: [router],
          stubs: { RadioPlayer: { template: '<section><slot /></section>' }, RadioText: true },
        },
      }
    )
    return { router, wrapper }
  }

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

  it('should go back to the day in Catch-up it was opened from', async () => {
    shown.value = recording()
    const router = createRouter({
      history: createWebHistory(),
      routes: [
        { path: '/radio/catch-up', name: 'RadioCatchUp', component: { template: '<p />' } },
        { path: '/radio/:name', component: RadioEpisode },
      ],
    })
    await router.push('/radio/catch-up?day=2026-10-14')
    await router.push(`/radio/${recording().name}`)
    const link = mount(
      { template: '<router-view />' },
      {
        global: {
          plugins: [router],
          stubs: { RadioPlayer: { template: '<section><slot /></section>' }, RadioText: true },
        },
      }
    ).get('a')
    expect([link.attributes('aria-label'), link.attributes('href')]).toEqual([
      'Back to Catch-up',
      '/radio/catch-up?day=2026-10-14',
    ])
  })

  it('should go back to the shows when opened on its own', async () => {
    const link = (await open(recording())).wrapper.get('a')
    expect([link.attributes('aria-label'), link.attributes('href')]).toEqual([
      'Back to Shows',
      '/radio',
    ])
  })

  it('should leave the name of the show to the player', async () => {
    const { wrapper } = await open(recording())
    expect(wrapper.find('h2').exists()).toBe(false)
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
