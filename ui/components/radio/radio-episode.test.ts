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
      routes: [
        {
          path: '/radio/:name',
          component: RadioEpisode,
          children: [{ path: '', component: { template: '<div />' } }],
        },
      ],
    })
    await router.push(`/radio/${item.name}`)
    mount({ template: '<router-view />' }, { global: { plugins: [router] } })
    return player.version.value
  }

  it('should open a recording on its cut version', async () => {
    expect(await open(recording())).toBe('episode')
  })

  it('should open a recording without a cut version on the raw recording', async () => {
    expect(await open(recording({ editList: null, episodeBytes: null }))).toBe('raw')
  })
})
