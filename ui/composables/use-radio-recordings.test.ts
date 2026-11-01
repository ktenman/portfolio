import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { Station } from '../models/generated/domain-models'
import { radioService } from '../services/api'
import { useRadioDay, useRadioSearch } from './use-radio-recordings'

vi.mock('../services/api', () => ({
  radioService: {
    day: vi.fn().mockResolvedValue({}),
    search: vi.fn().mockResolvedValue({ next: null }),
  },
}))
vi.mock('./use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const keys = async (use: () => unknown) => {
  const client = new QueryClient()
  const page = { setup: () => (use(), () => null) }
  mount(page, { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } })
  await flushPromises()
  return client
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
