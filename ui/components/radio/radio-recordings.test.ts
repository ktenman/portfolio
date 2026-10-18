import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { radioService } from '../../services/api'
import { renderWithProviders } from '../../tests/test-utils'
import RadioRecordings from './radio-recordings.vue'
import { recording } from './radio-fixture'
import { CutState, RecordingState, TranscriptState } from '../../models/generated/domain-models'

vi.mock('../../services/api', () => ({ radioService: { recordings: vi.fn() } }))
vi.mock('../../composables/use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const mount = async () => {
  const wrapper = renderWithProviders(RadioRecordings)
  await flushPromises()
  return wrapper
}

describe('RadioRecordings', () => {
  beforeEach(() => {
    vi.mocked(radioService.recordings).mockResolvedValue([
      recording(),
      recording({
        name: '2026-10-16',
        status: { state: RecordingState.RECORDING, startedAt: 1792170142, seconds: 60, parts: [] },
        sizeBytes: 1_600_000,
        transcribed: false,
        editList: null,
        episodeBytes: null,
        cuts: [],
        timeline: [],
        transcriptState: TranscriptState.RECORDING,
        cutState: CutState.NONE,
      }),
    ])
  })

  it('should count the recordings and their size in the header', async () => {
    expect((await mount()).text()).toContain('Kuku · 2 salvestust · 136 MB')
  })

  it('should show when and how long the show was recorded', async () => {
    expect((await mount()).text()).toContain('L 17.10 · 11:55–13:05')
  })

  it('should count the suggested cuts of a recording', async () => {
    expect((await mount()).text()).toContain('4 soovitust')
  })

  it('should say a transcript nobody has checked is unchecked', async () => {
    expect((await mount()).text()).toContain('Kontrollimata')
  })

  it('should say a recording in progress is being recorded', async () => {
    expect((await mount()).text()).toContain('Salvestab')
  })

  it('should name each listen link after its recording', async () => {
    const link = (await mount()).find('a[href="/radio/2026-10-17"]')
    expect(link.attributes('aria-label')).toBe('Kuula: Keskpäevatund L 17.10')
  })

  it('should link each recording to its episode page', async () => {
    const links = (await mount()).findAll('a[href^="/radio/"]').map(a => a.attributes('href'))
    expect(links).toContain('/radio/2026-10-17')
  })
})
