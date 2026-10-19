import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import RadioEpisode from './radio-episode.vue'
import { recording } from './radio-fixture'
import { useRadioPlayer } from '../../composables/use-radio-player'
import type { RecordingSummary } from '../../models/generated/domain-models'

const { shown } = vi.hoisted(() => ({ shown: { value: null as RecordingSummary | null } }))

vi.mock('../../composables/use-radio-recordings', () => ({
  useRadioRecording: () => ({ recording: ref(shown.value), isLoading: ref(false) }),
}))
vi.mock('../../services/api', () => ({
  radioService: { fileUrl: (name: string, file: string) => `/api/radio/${name}/${file}` },
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
      { global: { plugins: [router], stubs: { RadioPlayer: true, RadioText: true } } }
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
    expect(wrapper.find('nav[aria-label="Saate vaated"]').exists()).toBe(false)
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

  it('should send old cuts links to the recording', async () => {
    const { default: app } = await import('../../router')
    await app.push('/radio/2026-10-17/cuts')
    expect(app.currentRoute.value.fullPath).toBe('/radio/2026-10-17')
  })
})
