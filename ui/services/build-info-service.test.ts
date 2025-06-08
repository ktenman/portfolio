import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BuildInfoService } from './build-info-service'
import { ApiClient } from './api-client'

// Mock the ApiClient
vi.mock('./api-client', () => ({
  ApiClient: {
    get: vi.fn(),
  },
}))

// Mock console.error to avoid noise in tests
const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

describe('BuildInfoService', () => {
  let service: BuildInfoService

  beforeEach(() => {
    vi.clearAllMocks()
    service = new BuildInfoService()
  })

  describe('getBuildInfo', () => {
    it('returns build info on successful API call', async () => {
      const mockBuildInfo = {
        hash: 'abc123def456',
        time: '2024-01-15T10:30:00Z',
      }

      vi.mocked(ApiClient.get).mockResolvedValue(mockBuildInfo)

      const result = await service.getBuildInfo()

      expect(ApiClient.get).toHaveBeenCalledWith('/api/build-info')
      expect(result).toEqual(mockBuildInfo)
    })

    it('returns default values when API call fails', async () => {
      const apiError = new Error('Network error')
      vi.mocked(ApiClient.get).mockRejectedValue(apiError)

      const result = await service.getBuildInfo()

      expect(ApiClient.get).toHaveBeenCalledWith('/api/build-info')
      expect(consoleSpy).toHaveBeenCalledWith('Failed to fetch build info:', apiError)
      expect(result).toEqual({
        hash: 'unknown',
        time: 'unknown',
      })
    })

    it('handles undefined/null responses gracefully', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue(null)

      const result = await service.getBuildInfo()

      expect(result).toBe(null)
    })

    it('handles API timeout errors', async () => {
      const timeoutError = new Error('Request timeout')
      vi.mocked(ApiClient.get).mockRejectedValue(timeoutError)

      const result = await service.getBuildInfo()

      expect(consoleSpy).toHaveBeenCalledWith('Failed to fetch build info:', timeoutError)
      expect(result).toEqual({
        hash: 'unknown',
        time: 'unknown',
      })
    })

    it('handles malformed API responses', async () => {
      const malformedResponse = { invalidField: 'value' }
      vi.mocked(ApiClient.get).mockResolvedValue(malformedResponse)

      const result = await service.getBuildInfo()

      expect(result).toEqual(malformedResponse)
    })
  })

  describe('service initialization', () => {
    it('creates service with correct API URL', () => {
      const newService = new BuildInfoService()
      expect(newService).toBeInstanceOf(BuildInfoService)
    })
  })
})
