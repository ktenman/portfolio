import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import {
  dayDigest,
  digest,
  digestBuild,
  digestFailure,
} from '../components/radio/radio-digest-fixture'
import { type DigestBuild, type RecordingSummary, Station } from '../models/generated/domain-models'
import { radioService } from '../services/api'
import { renderWithProviders } from '../tests/test-utils'
import {
  useRadioDay,
  useRadioDigestBuild,
  useRadioRecording,
  useRadioSearch,
} from './use-radio-recordings'

vi.mock('../services/api', () => ({
  radioService: {
    day: vi.fn().mockResolvedValue({}),
    search: vi.fn().mockResolvedValue({ next: null }),
    recordings: vi.fn().mockResolvedValue([]),
    digests: vi.fn(),
    digestBuild: vi.fn(),
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
    expect(found.recording?.programme).toBe('Week in Radio 09.11–15.11.2026')
  })

  it('should find a day show by its name', async () => {
    vi.mocked(radioService.digests).mockResolvedValue([dayDigest(), digest()])
    const found: { recording?: RecordingSummary | null } = {}
    renderWithProviders({
      setup: () => {
        const { recording } = useRadioRecording('day-in-radio-2026-11-16')
        return () => (found.recording = recording.value) && null
      },
    })
    await flushPromises()
    expect(found.recording?.programme).toBe('Day in Radio')
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

describe('useRadioDigestBuild', () => {
  const asked = async (found: DigestBuild) => {
    vi.mocked(radioService.digestBuild).mockReset().mockResolvedValue(found)
    renderWithProviders({ setup: () => (useRadioDigestBuild(), () => null) })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(60_000)
    return vi.mocked(radioService.digestBuild).mock.calls.length
  }

  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should keep the build under the digests key', async () => {
    vi.mocked(radioService.digestBuild).mockResolvedValue(null)
    expect(await keys(useRadioDigestBuild)).toEqual([['radio-digests', 'build']])
  })

  it('should ask again each minute while the digest builds', async () => {
    expect(await asked(digestBuild())).toBe(2)
  })

  it('should not ask again while a failed digest waits for its next try', async () => {
    expect(await asked(digestBuild({ step: null, failure: digestFailure() }))).toBe(1)
  })
})
