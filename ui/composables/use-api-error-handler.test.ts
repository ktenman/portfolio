import { describe, it, expect } from 'vitest'
import { useApiErrorHandler } from './use-api-error-handler'
import { AlertType } from '../models/alert-type'
import { ApiError } from '../models/api-error'

describe('useApiErrorHandler', () => {
  describe('handleApiError', () => {
    it('handles ApiError correctly', () => {
      const { handleApiError, alertMessage, debugMessage, validationErrors, alertType } =
        useApiErrorHandler()

      const apiError = new ApiError(400, 'Test error', 'Debug message', { field1: 'Error 1' })
      handleApiError(apiError)

      expect(alertType.value).toBe(AlertType.ERROR)
      expect(alertMessage.value).toBe('Test error')
      expect(debugMessage.value).toBe('Debug message')
      expect(validationErrors.value).toEqual({ field1: 'Error 1' })
    })

    it('handles regular Error correctly', () => {
      const { handleApiError, alertMessage, debugMessage, validationErrors, alertType } =
        useApiErrorHandler()

      const error = new Error('Regular error')
      handleApiError(error)

      expect(alertType.value).toBe(AlertType.ERROR)
      expect(alertMessage.value).toBe('Regular error')
      expect(debugMessage.value).toBe('')
      expect(validationErrors.value).toEqual({})
    })

    it('handles unknown error correctly', () => {
      const { handleApiError, alertMessage, debugMessage, validationErrors, alertType } =
        useApiErrorHandler()

      handleApiError('string error')

      expect(alertType.value).toBe(AlertType.ERROR)
      expect(alertMessage.value).toBe('An unexpected error occurred')
      expect(debugMessage.value).toBe('')
      expect(validationErrors.value).toEqual({})
    })

    it('handles null/undefined error correctly', () => {
      const { handleApiError, alertMessage, debugMessage, validationErrors, alertType } =
        useApiErrorHandler()

      handleApiError(null)

      expect(alertType.value).toBe(AlertType.ERROR)
      expect(alertMessage.value).toBe('An unexpected error occurred')
      expect(debugMessage.value).toBe('')
      expect(validationErrors.value).toEqual({})
    })
  })

  describe('clearError', () => {
    it('clears all error state', () => {
      const {
        handleApiError,
        clearError,
        alertMessage,
        debugMessage,
        validationErrors,
        alertType,
      } = useApiErrorHandler()

      // Set some error state first
      const apiError = new ApiError(400, 'Test error', 'Debug message', { field1: 'Error 1' })
      handleApiError(apiError)

      // Clear the error
      clearError()

      expect(alertType.value).toBe(null)
      expect(alertMessage.value).toBe('')
      expect(debugMessage.value).toBe('')
      expect(validationErrors.value).toEqual({})
    })
  })

  describe('setSuccess', () => {
    it('sets success state correctly', () => {
      const { setSuccess, alertMessage, debugMessage, validationErrors, alertType } =
        useApiErrorHandler()

      setSuccess('Operation successful')

      expect(alertType.value).toBe(AlertType.SUCCESS)
      expect(alertMessage.value).toBe('Operation successful')
      expect(debugMessage.value).toBe('')
      expect(validationErrors.value).toEqual({})
    })

    it('overwrites previous error state', () => {
      const {
        handleApiError,
        setSuccess,
        alertMessage,
        debugMessage,
        validationErrors,
        alertType,
      } = useApiErrorHandler()

      // Set error first
      const apiError = new ApiError(400, 'Test error', 'Debug message', { field1: 'Error 1' })
      handleApiError(apiError)

      // Set success
      setSuccess('Success message')

      expect(alertType.value).toBe(AlertType.SUCCESS)
      expect(alertMessage.value).toBe('Success message')
      expect(debugMessage.value).toBe('')
      expect(validationErrors.value).toEqual({})
    })
  })

  describe('initial state', () => {
    it('has correct initial values', () => {
      const { alertMessage, debugMessage, validationErrors, alertType } = useApiErrorHandler()

      expect(alertType.value).toBe(null)
      expect(alertMessage.value).toBe('')
      expect(debugMessage.value).toBe('')
      expect(validationErrors.value).toEqual({})
    })
  })
})
