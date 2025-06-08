import { describe, it, expect } from 'vitest'
import { AlertType, getAlertBootstrapClass } from './alert-type'

describe('AlertType', () => {
  describe('getAlertBootstrapClass', () => {
    it('returns correct Bootstrap class for ERROR type', () => {
      expect(getAlertBootstrapClass(AlertType.ERROR)).toBe('alert-danger')
    })

    it('returns correct Bootstrap class for SUCCESS type', () => {
      expect(getAlertBootstrapClass(AlertType.SUCCESS)).toBe('alert-success')
    })

    it('returns default class for null input', () => {
      expect(getAlertBootstrapClass(null)).toBe('alert-danger')
    })

    it('returns default class for unknown alert type', () => {
      // Test the fallback case by bypassing TypeScript checks
      expect(getAlertBootstrapClass('unknown' as any)).toBe('alert-info')
    })
  })
})
