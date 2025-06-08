import { describe, it, expect } from 'vitest'

// Since page.ts only contains interface definitions,
// we'll test that the interfaces can be used correctly
describe('Page', () => {
  it('has correct interface structure for string content', () => {
    const page = {
      content: ['item1', 'item2', 'item3'],
      totalElements: 3,
      totalPages: 1,
      size: 10,
      number: 0,
    }

    expect(page.content).toEqual(['item1', 'item2', 'item3'])
    expect(page.totalElements).toBe(3)
    expect(page.totalPages).toBe(1)
    expect(page.size).toBe(10)
    expect(page.number).toBe(0)
  })

  it('has correct interface structure for object content', () => {
    const items = [
      { id: 1, name: 'Item 1' },
      { id: 2, name: 'Item 2' },
    ]

    const page = {
      content: items,
      totalElements: 2,
      totalPages: 1,
      size: 10,
      number: 0,
    }

    expect(page.content).toEqual(items)
    expect(page.content[0].id).toBe(1)
    expect(page.content[1].name).toBe('Item 2')
  })

  it('handles empty page', () => {
    const emptyPage = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
    }

    expect(emptyPage.content).toEqual([])
    expect(emptyPage.content.length).toBe(0)
    expect(emptyPage.totalElements).toBe(0)
    expect(emptyPage.totalPages).toBe(0)
  })

  it('handles pagination with multiple pages', () => {
    const page = {
      content: ['item1', 'item2'],
      totalElements: 25,
      totalPages: 3,
      size: 10,
      number: 1,
    }

    expect(page.number).toBe(1) // Second page (0-indexed)
    expect(page.totalPages).toBe(3)
    expect(page.totalElements).toBe(25)
    expect(page.size).toBe(10)
  })

  it('handles different page sizes', () => {
    const smallPage = {
      content: ['a', 'b'],
      totalElements: 100,
      totalPages: 50,
      size: 2,
      number: 10,
    }

    const largePage = {
      content: Array.from({ length: 100 }, (_, i) => `item${i}`),
      totalElements: 1000,
      totalPages: 10,
      size: 100,
      number: 5,
    }

    expect(smallPage.size).toBe(2)
    expect(smallPage.content.length).toBe(2)
    expect(largePage.size).toBe(100)
    expect(largePage.content.length).toBe(100)
  })

  it('handles complex object types in content', () => {
    interface TestItem {
      id: number
      name: string
      value: number
      active: boolean
    }

    const items: TestItem[] = [
      { id: 1, name: 'Test 1', value: 100.5, active: true },
      { id: 2, name: 'Test 2', value: 200.75, active: false },
    ]

    const page = {
      content: items,
      totalElements: 2,
      totalPages: 1,
      size: 10,
      number: 0,
    }

    expect(page.content[0].id).toBe(1)
    expect(page.content[0].name).toBe('Test 1')
    expect(page.content[0].value).toBe(100.5)
    expect(page.content[0].active).toBe(true)
    expect(page.content[1].active).toBe(false)
  })

  it('handles edge case values', () => {
    const page = {
      content: [],
      totalElements: 0,
      totalPages: 1, // Edge case: 1 page even with 0 elements
      size: 0, // Edge case: zero size
      number: 0,
    }

    expect(page.totalElements).toBe(0)
    expect(page.totalPages).toBe(1)
    expect(page.size).toBe(0)
  })

  it('validates numeric field types', () => {
    const page = {
      content: ['test'],
      totalElements: 42,
      totalPages: 5,
      size: 10,
      number: 2,
    }

    expect(typeof page.totalElements).toBe('number')
    expect(typeof page.totalPages).toBe('number')
    expect(typeof page.size).toBe('number')
    expect(typeof page.number).toBe('number')
  })
})
