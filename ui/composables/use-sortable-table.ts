import { ref, computed, type Ref } from 'vue'

type SortDirection = 'asc' | 'desc' | null

export interface SortState {
  key: string | null
  direction: SortDirection
}

export function useSortableTable<T extends Record<string, any>>(
  items: Ref<T[]>,
  defaultSortKey?: string,
  defaultDirection: SortDirection = 'asc'
) {
  const sortState = ref<SortState>({
    key: defaultSortKey || null,
    direction: defaultSortKey ? defaultDirection : null,
  })

  const sortedItems = computed(() => {
    if (!sortState.value.key || !sortState.value.direction) {
      return items.value
    }

    const sorted = [...items.value].sort((a, b) => {
      const key = sortState.value.key!
      const aValue = getNestedValue(a, key)
      const bValue = getNestedValue(b, key)

      if (aValue === null || aValue === undefined) return 1
      if (bValue === null || bValue === undefined) return -1

      let comparison = 0

      if (typeof aValue === 'number' && typeof bValue === 'number') {
        comparison = aValue - bValue
      } else if (aValue instanceof Date && bValue instanceof Date) {
        comparison = aValue.getTime() - bValue.getTime()
      } else {
        comparison = String(aValue).localeCompare(String(bValue))
      }

      return sortState.value.direction === 'asc' ? comparison : -comparison
    })

    return sorted
  })

  const toggleSort = (key: string) => {
    if (sortState.value.key === key) {
      if (sortState.value.direction === 'asc') {
        sortState.value.direction = 'desc'
      } else if (sortState.value.direction === 'desc') {
        sortState.value.direction = null
        sortState.value.key = null
      }
    } else {
      sortState.value.key = key
      sortState.value.direction = 'asc'
    }
  }

  const getSortDirection = (key: string): SortDirection => {
    if (sortState.value.key === key) {
      return sortState.value.direction
    }
    return null
  }

  return {
    sortedItems,
    sortState,
    toggleSort,
    getSortDirection,
  }
}

function getNestedValue(obj: any, path: string): any {
  const keys = path.split('.')
  let value = obj

  for (const key of keys) {
    value = value?.[key]
  }

  return value
}
