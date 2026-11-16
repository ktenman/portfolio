import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { digest } from '../components/radio/radio-digest-fixture'
import { type RecordingSummary, Station } from '../models/generated/domain-models'
import { radioService } from '../services/api'
import { renderWithProviders } from '../tests/test-utils'
import { useRadioDay, useRadioRecording, useRadioSearch } from './use-radio-recordings'

vi.mock('../services/api', () => ({
  radioService: {
    day: vi.fn().mockResolvedValue({}),
    search: vi.fn().mockResolvedValue({ next: null }),
    recordings: vi.fn().mockResolvedValue([]),
    digests: vi.fn(),
  },
}))
vi.mock('./use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const keys = async (use: () => unknown) => {
  const { queryClient } = renderWithProviders({ setup: () => (use(), () => null) })
  await flushPromises()
  return queryClient
    .getQueryCache()
    .getAll()
    .map(query => query.queryKey)
}

describe('useRadioDay', () => {
  it('should keep the day of each station under its own key', async () => {
    const found = await keys(() => useRadioDay('2026-10-19', Station.ARIPAEV))
    expect(found).toEqual([['radio-day', '2026-10-19', Station.ARIPAEV]])
  })

  it('should ask for the day of the station', async () => {
    await keys(() => useRadioDay('2026-10-19', Station.ARIPAEV))
    expect(radioService.day).toHaveBeenLastCalledWith('2026-10-19', Station.ARIPAEV)
  })
})

describe('useRadioSearch', () => {
  it('should keep the results of each station under their own key', async () => {
    const found = await keys(() => useRadioSearch('valimised', Station.ARIPAEV))
    expect(found).toEqual([['radio', 'search', 'valimised', Station.ARIPAEV]])
  })

  it('should search the station', async () => {
    await keys(() => useRadioSearch('valimised', Station.ARIPAEV))
    expect(radioService.search).toHaveBeenLastCalledWith('valimised', null, Station.ARIPAEV)
  })
})

describe('useRadioRecording', () => {
  it('should find a digest by name', async () => {
    vi.mocked(radioService.digests).mockResolvedValue([digest()])
    const found: { recording?: RecordingSummary | null } = {}
    renderWithProviders({
      setup: () => {
        const { recording } = useRadioRecording('week-in-radio-2026-11-16')
        return () => (found.recording = recording.value) && null
      },
    })
    await flushPromises()
    expect(found.recording?.programme).toBe('Week in Radio')
  })

  it('should not ask for digests on a recording', async () => {
    vi.mocked(radioService.digests).mockClear()
    renderWithProviders({
      setup: () => {
        useRadioRecording('2026-11-14-1200-keskpaevatund')
        return () => null
      },
    })
    await flushPromises()
    expect(radioService.digests).not.toHaveBeenCalled()
  })
})
