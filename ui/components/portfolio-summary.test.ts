import { beforeEach, describe, it, expect } from 'vitest'
import { formatCurrencyWithSymbol } from '../utils/formatters'
import { formatPlatformName, setPlatformDisplayNames } from '../utils/platform-utils'

describe('PortfolioSummary', () => {
  describe('platform filter', () => {
    const availablePlatforms = [
      'AVIVA',
      'BINANCE',
      'COINBASE',
      'IBKR',
      'LHV',
      'LIGHTYEAR',
      'SWEDBANK',
      'TRADING212',
    ]

    beforeEach(() => {
      setPlatformDisplayNames([
        { name: 'AVIVA', displayName: 'Aviva' },
        { name: 'BINANCE', displayName: 'Binance' },
        { name: 'COINBASE', displayName: 'Coinbase' },
        { name: 'IBKR', displayName: 'IBKR' },
        { name: 'LHV', displayName: 'LHV' },
        { name: 'LIGHTYEAR', displayName: 'Lightyear' },
        { name: 'SWEDBANK', displayName: 'Swedbank' },
        { name: 'TRADING212', displayName: 'Trading 212' },
      ])
    })

    it('should format all available platform names', () => {
      const formatted = availablePlatforms.map(formatPlatformName)
      expect(formatted).toContain('Trading 212')
      expect(formatted).toContain('Lightyear')
      expect(formatted).toContain('Binance')
      expect(formatted).toContain('LHV')
    })

    it('should toggle platform selection correctly', () => {
      let selected: string[] = []
      const toggle = (platform: string) => {
        const index = selected.indexOf(platform)
        if (index > -1) {
          selected = selected.filter(p => p !== platform)
        } else {
          selected = [...selected, platform]
        }
      }

      toggle('LIGHTYEAR')
      expect(selected).toEqual(['LIGHTYEAR'])

      toggle('TRADING212')
      expect(selected).toEqual(['LIGHTYEAR', 'TRADING212'])

      toggle('LIGHTYEAR')
      expect(selected).toEqual(['TRADING212'])
    })

    it('should toggle all platforms', () => {
      let selected: string[] = []
      const toggleAll = () => {
        if (selected.length === availablePlatforms.length) {
          selected = []
        } else {
          selected = [...availablePlatforms]
        }
      }

      toggleAll()
      expect(selected).toHaveLength(availablePlatforms.length)

      toggleAll()
      expect(selected).toHaveLength(0)
    })
  })

  describe('format24hChange', () => {
    const format24hChange = (value: number | null) => {
      if (value === null || value === 0 || Math.abs(value) <= 0.01) {
        return ''
      }
      return formatCurrencyWithSymbol(value)
    }

    it('should format positive 24h change', () => {
      const result = format24hChange(250.5)
      expect(result).toBe('€250.50')
    })

    it('should format negative 24h change', () => {
      const result = format24hChange(-125.75)
      expect(result).toBe('-€125.75')
    })

    it('should return empty string for zero 24h change', () => {
      const result = format24hChange(0)
      expect(result).toBe('')
    })

    it('should return empty string for null 24h change', () => {
      const result = format24hChange(null)
      expect(result).toBe('')
    })

    it('should return empty string for near-zero positive 24h change (< 0.01)', () => {
      const result = format24hChange(0.005)
      expect(result).toBe('')
    })

    it('should return empty string for near-zero negative 24h change (> -0.01)', () => {
      const result = format24hChange(-0.005)
      expect(result).toBe('')
    })

    it('should format value exactly at threshold (0.01)', () => {
      const result = format24hChange(0.01)
      expect(result).toBe('')
    })

    it('should format value just above threshold (0.011)', () => {
      const result = format24hChange(0.011)
      expect(result).toBe('€0.01')
    })
  })

  describe('getProfitChangeClass', () => {
    const getProfitChangeClass = (value: number) => {
      if (value > 0) return 'text-success'
      if (value < 0) return 'text-danger'
      return ''
    }

    it('should return text-success for positive values', () => {
      expect(getProfitChangeClass(100)).toBe('text-success')
      expect(getProfitChangeClass(0.01)).toBe('text-success')
    })

    it('should return text-danger for negative values', () => {
      expect(getProfitChangeClass(-100)).toBe('text-danger')
      expect(getProfitChangeClass(-0.01)).toBe('text-danger')
    })

    it('should return empty string for zero', () => {
      expect(getProfitChangeClass(0)).toBe('')
    })
  })
})
