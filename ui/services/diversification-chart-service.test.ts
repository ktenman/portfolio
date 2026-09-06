import { describe, it, expect } from 'vitest'
import { activeShare, compareBreakdown, isFlagged } from './diversification-chart-service'

const OPTS = { topCount: 2, minPercentage: 0.5, withOther: true }

describe('isFlagged', () => {
  it('flags ratios above 2 and below 0.5 only', () => {
    expect([2.03, 0.44, 1.86, undefined].map(isFlagged)).toEqual([true, true, false, false])
  })
})

describe('compareBreakdown', () => {
  const items = [
    { label: 'Banks', value: 19 },
    { label: 'Software', value: 7 },
    { label: 'Pharma', value: 1.2 },
    { label: 'Tiny', value: 0.2 },
  ]
  const benchmark = [
    { label: 'Banks', value: 8 },
    { label: 'Software', value: 7.1 },
    { label: 'Pharma', value: 4.1 },
    { label: 'Ålands Bank', value: 0.1 },
  ]

  it('keeps the top rows and folds the rest into Other so values sum to the total', () => {
    const rows = compareBreakdown(items, null, OPTS)
    expect(rows.map(r => r.label)).toEqual(['Banks', 'Software', 'Other'])
    expect(rows.reduce((s, r) => s + r.value, 0)).toBeCloseTo(27.4)
    expect(rows[2].isOther).toBe(true)
  })

  it('attaches benchmark share and ratio to shown rows', () => {
    const rows = compareBreakdown(items, benchmark, OPTS)
    expect(rows[0]).toMatchObject({ benchmark: 8, ratio: 2.375 })
  })

  it('leaves ratio undefined below the minimum benchmark share', () => {
    const rows = compareBreakdown([{ label: 'A', value: 5 }], [{ label: 'A', value: 0.004 }], OPTS)
    expect(rows[0].ratio).toBeUndefined()
  })

  it('carries the benchmark residual on the Other row without a ratio', () => {
    const rows = compareBreakdown(items, benchmark, OPTS)
    expect(rows[2].benchmark).toBeCloseTo(4.2)
    expect(rows[2].ratio).toBeUndefined()
  })

  it('emits Other when only the benchmark has a residual', () => {
    const rows = compareBreakdown(
      [{ label: 'A', value: 100 }],
      [
        { label: 'A', value: 50 },
        { label: 'B', value: 50 },
      ],
      OPTS
    )
    expect(rows[rows.length - 1]).toMatchObject({ label: 'Other', value: 0, benchmark: 50 })
  })

  it('joins holdings by normalised name', () => {
    const rows = compareBreakdown(
      [{ label: 'Nvidia  Corp', value: 4 }],
      [{ label: 'NVIDIA corp', value: 2 }],
      OPTS
    )
    expect(rows[0].ratio).toBe(2)
  })

  it('emits no Other row when withOther is false', () => {
    const rows = compareBreakdown(items, benchmark, { ...OPTS, withOther: false })
    expect(rows.map(r => r.label)).toEqual(['Banks', 'Software'])
  })

  it('keeps the country code', () => {
    const rows = compareBreakdown([{ label: 'Spain', value: 5, code: 'ES' }], null, OPTS)
    expect(rows[0].code).toBe('ES')
  })
})

describe('activeShare', () => {
  it('is half the sum of absolute weight differences', () => {
    const own = [
      { label: 'A', value: 60 },
      { label: 'B', value: 40 },
    ]
    const benchmark = [
      { label: 'A', value: 40 },
      { label: 'B', value: 60 },
    ]
    expect(activeShare(own, benchmark)).toBe(20)
  })

  it('compares the shape of each side when the lists do not sum to 100', () => {
    const own = [
      { label: 'A', value: 30 },
      { label: 'B', value: 20 },
    ]
    const benchmark = [
      { label: 'A', value: 60 },
      { label: 'B', value: 40 },
    ]
    expect(activeShare(own, benchmark)).toBe(0)
  })

  it('counts labels present on one side only', () => {
    expect(activeShare([{ label: 'A', value: 100 }], [{ label: 'B', value: 100 }])).toBe(100)
  })
})
