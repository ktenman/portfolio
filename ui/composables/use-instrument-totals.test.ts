import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useInstrumentTotals } from './use-instrument-totals'
import type { InstrumentDto } from '../models/generated/domain-models'

const createInstrument = (overrides: Partial<InstrumentDto> = {}): InstrumentDto => ({
  id: 1,
  symbol: 'AAPL',
  name: 'Apple Inc.',
  category: 'STOCK',
  baseCurrency: 'USD',
  currentPrice: 150,
  quantity: 10,
  currentValue: 1500,
  totalInvestment: 1000,
  profit: 500,
  realizedProfit: 0,
  unrealizedProfit: 500,
  priceChangeAmount: 50,
  priceChangePercent: 3.45,
  xirr: 0.15,
  providerName: 'FT',
  platforms: ['TRADING212'],
  ter: null,
  ...overrides,
})

describe('useInstrumentTotals', () => {
  describe('totalInvested', () => {
    it('should sum totalInvestment from all instruments', () => {
      const instruments = ref([
        createInstrument({ totalInvestment: 1000 }),
        createInstrument({ totalInvestment: 2000 }),
        createInstrument({ totalInvestment: 3000 }),
      ])
      const { totalInvested } = useInstrumentTotals(instruments)
      expect(totalInvested.value).toBe(6000)
    })

    it('should return 0 for empty array', () => {
      const instruments = ref<InstrumentDto[]>([])
      const { totalInvested } = useInstrumentTotals(instruments)
      expect(totalInvested.value).toBe(0)
    })

    it('should handle null totalInvestment values', () => {
      const instruments = ref([
        createInstrument({ totalInvestment: 1000 }),
        createInstrument({ totalInvestment: undefined }),
        createInstrument({ totalInvestment: 2000 }),
      ])
      const { totalInvested } = useInstrumentTotals(instruments)
      expect(totalInvested.value).toBe(3000)
    })
  })

  describe('totalValue', () => {
    it('should sum currentValue from all instruments', () => {
      const instruments = ref([
        createInstrument({ currentValue: 1500 }),
        createInstrument({ currentValue: 2500 }),
        createInstrument({ currentValue: 3500 }),
      ])
      const { totalValue } = useInstrumentTotals(instruments)
      expect(totalValue.value).toBe(7500)
    })

    it('should return 0 for empty array', () => {
      const instruments = ref<InstrumentDto[]>([])
      const { totalValue } = useInstrumentTotals(instruments)
      expect(totalValue.value).toBe(0)
    })

    it('should handle undefined currentValue', () => {
      const instruments = ref([
        createInstrument({ currentValue: 1000 }),
        createInstrument({ currentValue: undefined }),
      ])
      const { totalValue } = useInstrumentTotals(instruments)
      expect(totalValue.value).toBe(1000)
    })
  })

  describe('totalProfit', () => {
    it('should sum profit from all instruments', () => {
      const instruments = ref([
        createInstrument({ profit: 100 }),
        createInstrument({ profit: 200 }),
        createInstrument({ profit: 300 }),
      ])
      const { totalProfit } = useInstrumentTotals(instruments)
      expect(totalProfit.value).toBe(600)
    })

    it('should handle negative profits', () => {
      const instruments = ref([
        createInstrument({ profit: 500 }),
        createInstrument({ profit: -200 }),
        createInstrument({ profit: -100 }),
      ])
      const { totalProfit } = useInstrumentTotals(instruments)
      expect(totalProfit.value).toBe(200)
    })

    it('should return 0 for empty array', () => {
      const instruments = ref<InstrumentDto[]>([])
      const { totalProfit } = useInstrumentTotals(instruments)
      expect(totalProfit.value).toBe(0)
    })
  })

  describe('totalUnrealizedProfit', () => {
    it('should sum unrealizedProfit from all instruments', () => {
      const instruments = ref([
        createInstrument({ unrealizedProfit: 150 }),
        createInstrument({ unrealizedProfit: 250 }),
      ])
      const { totalUnrealizedProfit } = useInstrumentTotals(instruments)
      expect(totalUnrealizedProfit.value).toBe(400)
    })

    it('should handle negative unrealized profits', () => {
      const instruments = ref([
        createInstrument({ unrealizedProfit: 300 }),
        createInstrument({ unrealizedProfit: -100 }),
      ])
      const { totalUnrealizedProfit } = useInstrumentTotals(instruments)
      expect(totalUnrealizedProfit.value).toBe(200)
    })

    it('should return 0 for empty array', () => {
      const instruments = ref<InstrumentDto[]>([])
      const { totalUnrealizedProfit } = useInstrumentTotals(instruments)
      expect(totalUnrealizedProfit.value).toBe(0)
    })
  })

  describe('totalChangeAmount', () => {
    it('should sum priceChangeAmount from all instruments', () => {
      const instruments = ref([
        createInstrument({ priceChangeAmount: 50 }),
        createInstrument({ priceChangeAmount: 75 }),
        createInstrument({ priceChangeAmount: 25 }),
      ])
      const { totalChangeAmount } = useInstrumentTotals(instruments)
      expect(totalChangeAmount.value).toBe(150)
    })

    it('should handle negative price changes', () => {
      const instruments = ref([
        createInstrument({ priceChangeAmount: 100 }),
        createInstrument({ priceChangeAmount: -50 }),
      ])
      const { totalChangeAmount } = useInstrumentTotals(instruments)
      expect(totalChangeAmount.value).toBe(50)
    })

    it('should return 0 for empty array', () => {
      const instruments = ref<InstrumentDto[]>([])
      const { totalChangeAmount } = useInstrumentTotals(instruments)
      expect(totalChangeAmount.value).toBe(0)
    })
  })

  describe('totalChangePercent', () => {
    it('should calculate percentage based on previous value', () => {
      const instruments = ref([createInstrument({ currentValue: 1100, priceChangeAmount: 100 })])
      const { totalChangePercent } = useInstrumentTotals(instruments)
      expect(totalChangePercent.value).toBe(10)
    })

    it('should return 0 when previous value is 0', () => {
      const instruments = ref([createInstrument({ currentValue: 100, priceChangeAmount: 100 })])
      const { totalChangePercent } = useInstrumentTotals(instruments)
      expect(totalChangePercent.value).toBe(0)
    })

    it('should handle negative percentage', () => {
      const instruments = ref([createInstrument({ currentValue: 900, priceChangeAmount: -100 })])
      const { totalChangePercent } = useInstrumentTotals(instruments)
      expect(totalChangePercent.value).toBe(-10)
    })

    it('should return 0 for empty array', () => {
      const instruments = ref<InstrumentDto[]>([])
      const { totalChangePercent } = useInstrumentTotals(instruments)
      expect(totalChangePercent.value).toBe(0)
    })
  })

  describe('reactivity', () => {
    it('should update totals when instruments change', () => {
      const instruments = ref([createInstrument({ totalInvestment: 1000 })])
      const { totalInvested } = useInstrumentTotals(instruments)
      expect(totalInvested.value).toBe(1000)

      instruments.value = [
        createInstrument({ totalInvestment: 1000 }),
        createInstrument({ totalInvestment: 2000 }),
      ]
      expect(totalInvested.value).toBe(3000)
    })

    it('should update when instrument is added', () => {
      const instruments = ref([createInstrument({ currentValue: 500 })])
      const { totalValue } = useInstrumentTotals(instruments)
      expect(totalValue.value).toBe(500)

      instruments.value.push(createInstrument({ currentValue: 300 }))
      expect(totalValue.value).toBe(800)
    })
  })
})
