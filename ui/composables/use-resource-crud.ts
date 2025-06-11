import { ref, Ref } from 'vue'
import { useApi } from './use-api'
import { Page } from '../models/page'

interface CrudOptions {
  immediate?: boolean
  pageSize?: number
}

interface UseResourceCrudReturn<T> {
  items: Ref<T[]>
  page: Ref<Page<T> | null>
  selectedItem: Ref<T | null>
  isLoading: Ref<boolean>
  isCreating: Ref<boolean>
  isUpdating: Ref<boolean>
  isDeleting: Ref<boolean>
  error: Ref<Error | null>

  fetchAll: () => Promise<void>
  fetchPage: (pageNumber: number, size?: number) => Promise<void>
  create: (item: Partial<T>) => Promise<T | null>
  update: (id: string | number, item: Partial<T>) => Promise<T | null>
  remove: (id: string | number) => Promise<boolean>
  select: (item: T | null) => void
}

export function useResourceCrud<T extends { id?: string | number }>(
  apiService: any,
  options: CrudOptions = {}
): UseResourceCrudReturn<T> {
  const items = ref<T[]>([]) as Ref<T[]>
  const page = ref<Page<T> | null>(null) as Ref<Page<T> | null>
  const selectedItem = ref<T | null>(null) as Ref<T | null>
  const error = ref<Error | null>(null)

  // Create separate API composables for each operation
  const { isLoading, execute: executeGet } = useApi(() => apiService.getAll(), {
    immediate: options.immediate,
  })

  const { isLoading: isCreating, execute: executeCreate } = useApi((item: Partial<T>) =>
    apiService.create(item)
  )

  const { isLoading: isUpdating, execute: executeUpdate } = useApi(
    (id: string | number, item: Partial<T>) => apiService.update(id, item)
  )

  const { isLoading: isDeleting, execute: executeDelete } = useApi((id: string | number) =>
    apiService.delete(id)
  )

  const { execute: executeGetPage } = useApi((pageNumber: number, size: number) =>
    apiService.getPage(pageNumber, size)
  )

  const fetchAll = async () => {
    error.value = null
    const result = await executeGet()
    if (result) {
      items.value = result as T[]
    }
  }

  const fetchPage = async (pageNumber: number, size = options.pageSize || 20) => {
    error.value = null
    const result = await executeGetPage(pageNumber, size)
    if (result) {
      page.value = result as Page<T>
      items.value = (result as Page<T>).content
    }
  }

  const create = async (item: Partial<T>): Promise<T | null> => {
    error.value = null
    const result = await executeCreate(item)
    if (result) {
      await fetchAll() // Refresh the list
      return result as T
    }
    return null
  }

  const update = async (id: string | number, item: Partial<T>): Promise<T | null> => {
    error.value = null
    const result = await executeUpdate(id, item)
    if (result) {
      await fetchAll() // Refresh the list
      return result as T
    }
    return null
  }

  const remove = async (id: string | number): Promise<boolean> => {
    error.value = null
    const result = await executeDelete(id)
    if (result !== null) {
      await fetchAll() // Refresh the list
      return true
    }
    return false
  }

  const select = (item: T | null) => {
    selectedItem.value = item
  }

  return {
    items,
    page,
    selectedItem,
    isLoading,
    isCreating,
    isUpdating,
    isDeleting,
    error,
    fetchAll,
    fetchPage,
    create,
    update,
    remove,
    select,
  }
}
