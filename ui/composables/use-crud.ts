import { ref, Ref } from 'vue'
import { ICrudService } from '../types/service-interfaces'

export function useCrud<T extends { id?: number | string }>(service: ICrudService<T>) {
  const items = ref<T[]>([]) as Ref<T[]>
  const isLoading = ref(false)
  const error = ref<Error | null>(null)

  const fetchAll = async () => {
    isLoading.value = true
    error.value = null
    try {
      items.value = await service.getAll()
    } catch (err) {
      error.value = err instanceof Error ? err : new Error('Failed to fetch data')
      throw err
    } finally {
      isLoading.value = false
    }
  }

  const create = async (item: Partial<T>): Promise<T> => {
    isLoading.value = true
    error.value = null
    try {
      const newItem = await service.create(item)
      items.value.push(newItem)
      return newItem
    } catch (err) {
      error.value = err instanceof Error ? err : new Error('Failed to create item')
      throw err
    } finally {
      isLoading.value = false
    }
  }

  const update = async (id: number | string, item: Partial<T>): Promise<T> => {
    isLoading.value = true
    error.value = null
    try {
      const updatedItem = await service.update(id, item)
      const index = items.value.findIndex(i => i.id === id)
      if (index !== -1) {
        items.value[index] = updatedItem
      }
      return updatedItem
    } catch (err) {
      error.value = err instanceof Error ? err : new Error('Failed to update item')
      throw err
    } finally {
      isLoading.value = false
    }
  }

  const remove = async (id: number | string) => {
    isLoading.value = true
    error.value = null
    try {
      await service.delete(id)
      items.value = items.value.filter(item => item.id !== id)
    } catch (err) {
      error.value = err instanceof Error ? err : new Error('Failed to delete item')
      throw err
    } finally {
      isLoading.value = false
    }
  }

  return {
    items,
    isLoading,
    error,
    fetchAll,
    create,
    update,
    remove,
  }
}
