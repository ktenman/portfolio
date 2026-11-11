import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { ApiError } from '../models/api-error'
import { poffService } from '../services/api'
import { usePoffCatalogue } from './use-poff-catalogue'

vi.mock('../services/api', () => ({ poffService: { catalogue: vi.fn() } }))
vi.mock('./use-auth-state', async () => {
  const { ref } = await import('vue')
  return { useAuthState: () => ({ isAuthenticated: ref(true) }) }
})

enableAutoUnmount(afterEach)

const requests = async (error: ApiError) => {
  vi.mocked(poffService.catalogue).mockRejectedValue(error)
  mount(
    defineComponent({
      setup() {
        usePoffCatalogue()
        return () => null
      },
    }),
    { global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient() }]] } }
  )
  await vi.advanceTimersByTimeAsync(10_000)
  return vi.mocked(poffService.catalogue).mock.calls.length
}

describe('usePoffCatalogue', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(poffService.catalogue).mockReset()
  })

  afterEach(() => vi.useRealTimers())

  it('asks once when the programme is not imported yet', async () => {
    expect(await requests(new ApiError(404, 'Not Found', ''))).toBe(1)
  })

  it('asks again three times when the programme fails to load', async () => {
    expect(await requests(new ApiError(502, 'Bad Gateway', ''))).toBe(4)
  })
})
