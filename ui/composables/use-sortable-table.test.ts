import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { useSortableTable } from './use-sortable-table'

describe('useSortableTable', () => {
  const mockData = [
    { id: 1, name: 'Charlie', age: 30, date: new Date('2020-01-01'), nested: { value: 10 } },
    { id: 2, name: 'Alice', age: 25, date: new Date('2021-01-01'), nested: { value: 5 } },
    { id: 3, name: 'Bob', age: 35, date: new Date('2019-01-01'), nested: { value: 15 } },
    { id: 4, name: null, age: null, date: null, nested: null },
  ] as any
  describe('initialization', () => {
    it('should initialize with default sort state when no defaults provided', () => {
      const items = ref(mockData)
      const { sortState, sortedItems } = useSortableTable(items)

      expect(sortState.value.key).toBe(null)
      expect(sortState.value.direction).toBe(null)
      expect(sortedItems.value).toEqual(mockData)
    })

    it('should initialize with provided default sort key and direction', () => {
      const items = ref(mockData)
      const { sortState, sortedItems } = useSortableTable(items, 'name', 'desc')

      expect(sortState.value.key).toBe('name')
      expect(sortState.value.direction).toBe('desc')
      expect(sortedItems.value).not.toEqual(mockData)
    })

    it('should initialize with ascending direction by default when sort key provided', () => {
      const items = ref(mockData)
      const { sortState } = useSortableTable(items, 'name')

      expect(sortState.value.key).toBe('name')
      expect(sortState.value.direction).toBe('asc')
    })
  })

  describe('sorting strings', () => {
    it('should sort strings in ascending order', () => {
      const items = ref(mockData)
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('name')

      expect(sortedItems.value[0].name).toBe('Alice')
      expect(sortedItems.value[1].name).toBe('Bob')
      expect(sortedItems.value[2].name).toBe('Charlie')
      expect(sortedItems.value[3].name).toBe(null)
    })

    it('should sort strings in descending order', () => {
      const items = ref(mockData)
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('name')
      toggleSort('name')

      expect(sortedItems.value[0].name).toBe('Charlie')
      expect(sortedItems.value[1].name).toBe('Bob')
      expect(sortedItems.value[2].name).toBe('Alice')
    })
  })

  describe('sorting numbers', () => {
    it('should sort numbers in ascending order', () => {
      const items = ref(mockData)
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('age')

      expect(sortedItems.value[0].age).toBe(25)
      expect(sortedItems.value[1].age).toBe(30)
      expect(sortedItems.value[2].age).toBe(35)
      expect(sortedItems.value[3].age).toBe(null)
    })

    it('should sort numbers in descending order', () => {
      const items = ref(mockData)
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('age')
      toggleSort('age')

      expect(sortedItems.value[0].age).toBe(35)
      expect(sortedItems.value[1].age).toBe(30)
      expect(sortedItems.value[2].age).toBe(25)
    })
  })

  describe('sorting dates', () => {
    it('should sort dates in ascending order', () => {
      const items = ref(mockData)
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('date')

      expect(sortedItems.value[0].date?.getFullYear()).toBe(2019)
      expect(sortedItems.value[1].date?.getFullYear()).toBe(2020)
      expect(sortedItems.value[2].date?.getFullYear()).toBe(2021)
    })

    it('should sort dates in descending order', () => {
      const items = ref(mockData)
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('date')
      toggleSort('date')

      expect(sortedItems.value[0].date?.getFullYear()).toBe(2021)
      expect(sortedItems.value[1].date?.getFullYear()).toBe(2020)
      expect(sortedItems.value[2].date?.getFullYear()).toBe(2019)
    })
  })

  describe('nested property sorting', () => {
    it('should sort by nested property', () => {
      const items = ref(mockData)
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('nested.value')

      expect(sortedItems.value[0].nested?.value).toBe(5)
      expect(sortedItems.value[1].nested?.value).toBe(10)
      expect(sortedItems.value[2].nested?.value).toBe(15)
    })
  })

  describe('toggleSort', () => {
    it('should toggle between asc and desc', () => {
      const items = ref(mockData)
      const { toggleSort, sortState } = useSortableTable(items)

      toggleSort('name')
      expect(sortState.value.direction).toBe('asc')

      toggleSort('name')
      expect(sortState.value.direction).toBe('desc')

      toggleSort('name')
      expect(sortState.value.direction).toBe('asc')
      expect(sortState.value.key).toBe('name')
    })

    it('should reset sort when switching to different column', () => {
      const items = ref(mockData)
      const { toggleSort, sortState } = useSortableTable(items)

      toggleSort('name')
      expect(sortState.value.key).toBe('name')
      expect(sortState.value.direction).toBe('asc')

      toggleSort('age')
      expect(sortState.value.key).toBe('age')
      expect(sortState.value.direction).toBe('asc')
    })
  })

  describe('getSortDirection', () => {
    it('should return current sort direction for active column', () => {
      const items = ref(mockData)
      const { toggleSort, getSortDirection } = useSortableTable(items)

      toggleSort('name')
      expect(getSortDirection('name')).toBe('asc')

      toggleSort('name')
      expect(getSortDirection('name')).toBe('desc')
    })

    it('should return null for inactive column', () => {
      const items = ref(mockData)
      const { toggleSort, getSortDirection } = useSortableTable(items)

      toggleSort('name')
      expect(getSortDirection('age')).toBe(null)
    })

    it('should return null when no sort is active', () => {
      const items = ref(mockData)
      const { getSortDirection } = useSortableTable(items)

      expect(getSortDirection('name')).toBe(null)
    })
  })

  describe('null and undefined handling', () => {
    it('should place null values at the end when sorting ascending', () => {
      const items = ref(mockData)
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('name')

      const lastItem = sortedItems.value[sortedItems.value.length - 1]
      expect(lastItem.name).toBe(null)
    })

    it('should handle undefined nested values', () => {
      const items = ref([
        { id: 1, nested: { value: 10 } },
        { id: 2, nested: undefined },
        { id: 3, nested: { value: 5 } },
      ])
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('nested.value')

      expect(sortedItems.value[0].nested?.value).toBe(5)
      expect(sortedItems.value[1].nested?.value).toBe(10)
      expect(sortedItems.value[2].nested).toBe(undefined)
    })
  })

  describe('reactive updates', () => {
    it('should update sorted items when source items change', () => {
      const items = ref([...mockData])
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('name')
      expect(sortedItems.value).toHaveLength(4)

      items.value = [
        { id: 5, name: 'Zack', age: 40, date: new Date(), nested: { value: 20 } },
        ...items.value,
      ] as any
      expect(sortedItems.value).toHaveLength(5)
      expect(sortedItems.value[0].name).toBe('Alice')
      expect(sortedItems.value[3].name).toBe('Zack')
      expect(sortedItems.value[4].name).toBe(null)
    })
  })

  describe('totalProfitChange24h special handling', () => {
    const profitChangeData = [
      { id: 1, totalProfitChange24h: 100.5 },
      { id: 2, totalProfitChange24h: -50.25 },
      { id: 3, totalProfitChange24h: 0 },
      { id: 4, totalProfitChange24h: 0.005 },
      { id: 5, totalProfitChange24h: null },
      { id: 6, totalProfitChange24h: 200.75 },
      { id: 7, totalProfitChange24h: -100.0 },
    ]

    it('should treat zero values as null when sorting totalProfitChange24h ascending', () => {
      const items = ref(profitChangeData)
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('totalProfitChange24h')

      expect(sortedItems.value[0].totalProfitChange24h).toBe(-100.0)
      expect(sortedItems.value[1].totalProfitChange24h).toBe(-50.25)
      expect(sortedItems.value[2].totalProfitChange24h).toBe(100.5)
      expect(sortedItems.value[3].totalProfitChange24h).toBe(200.75)
      expect([0, 0.005, null]).toContain(sortedItems.value[4].totalProfitChange24h)
      expect([0, 0.005, null]).toContain(sortedItems.value[5].totalProfitChange24h)
      expect([0, 0.005, null]).toContain(sortedItems.value[6].totalProfitChange24h)
    })

    it('should treat zero values as null when sorting totalProfitChange24h descending', () => {
      const items = ref(profitChangeData)
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('totalProfitChange24h')
      toggleSort('totalProfitChange24h')

      expect(sortedItems.value[0].totalProfitChange24h).toBe(200.75)
      expect(sortedItems.value[1].totalProfitChange24h).toBe(100.5)
      expect(sortedItems.value[2].totalProfitChange24h).toBe(-50.25)
      expect(sortedItems.value[3].totalProfitChange24h).toBe(-100.0)
      expect([0, 0.005, null]).toContain(sortedItems.value[4].totalProfitChange24h)
      expect([0, 0.005, null]).toContain(sortedItems.value[5].totalProfitChange24h)
      expect([0, 0.005, null]).toContain(sortedItems.value[6].totalProfitChange24h)
    })

    it('should treat near-zero values (< 0.01) as null when sorting totalProfitChange24h', () => {
      const items = ref([
        { id: 1, totalProfitChange24h: 50 },
        { id: 2, totalProfitChange24h: 0.009 },
        { id: 3, totalProfitChange24h: -0.005 },
        { id: 4, totalProfitChange24h: 100 },
      ])
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('totalProfitChange24h')

      expect(sortedItems.value[0].totalProfitChange24h).toBe(50)
      expect(sortedItems.value[1].totalProfitChange24h).toBe(100)
      expect(sortedItems.value[2].totalProfitChange24h).toBe(0.009)
      expect(sortedItems.value[3].totalProfitChange24h).toBe(-0.005)
    })

    it('should not apply special handling to other numeric columns', () => {
      const items = ref([
        { id: 1, value: 0 },
        { id: 2, value: 100 },
        { id: 3, value: -50 },
      ])
      const { toggleSort, sortedItems } = useSortableTable(items)

      toggleSort('value')

      expect(sortedItems.value[0].value).toBe(-50)
      expect(sortedItems.value[1].value).toBe(0)
      expect(sortedItems.value[2].value).toBe(100)
    })
  })
})
