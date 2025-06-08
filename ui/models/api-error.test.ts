import { describe, it, expect } from 'vitest'
import { ApiError } from './api-error'

describe('ApiError', () => {
  describe('constructor', () => {
    it('creates an ApiError with all properties', () => {
      const validationErrors = { name: 'Name is required' }
      const error = new ApiError(400, 'Bad Request', 'Validation failed', validationErrors)

      expect(error.status).toBe(400)
      expect(error.message).toBe('Bad Request')
      expect(error.debugMessage).toBe('Validation failed')
      expect(error.validationErrors).toEqual(validationErrors)
      expect(error.name).toBe('ApiError')
      expect(error).toBeInstanceOf(Error)
    })

    it('creates an ApiError with default validation errors', () => {
      const error = new ApiError(500, 'Internal Server Error', 'Something went wrong')

      expect(error.status).toBe(500)
      expect(error.message).toBe('Internal Server Error')
      expect(error.debugMessage).toBe('Something went wrong')
      expect(error.validationErrors).toEqual({})
      expect(error.name).toBe('ApiError')
    })
  })

  describe('isApiError', () => {
    it('returns true for ApiError instances', () => {
      const error = new ApiError(404, 'Not Found', 'Resource not found')
      expect(ApiError.isApiError(error)).toBe(true)
    })

    it('returns false for regular Error instances', () => {
      const error = new Error('Regular error')
      expect(ApiError.isApiError(error)).toBe(false)
    })

    it('returns false for non-error objects', () => {
      expect(ApiError.isApiError({})).toBe(false)
      expect(ApiError.isApiError('string')).toBe(false)
      expect(ApiError.isApiError(null)).toBe(false)
      expect(ApiError.isApiError(undefined)).toBe(false)
      expect(ApiError.isApiError(42)).toBe(false)
    })
  })
})
