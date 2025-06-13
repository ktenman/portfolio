import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { Ref } from 'vue'
import { useToast } from 'vue-toastification'

interface CrudOptions<T> {
  queryKey: string[]
  createFn: (data: Partial<T>) => Promise<T>
  updateFn: (id: number | string, data: Partial<T>) => Promise<T>
  deleteFn?: (id: number | string) => Promise<void>
  entityName: string
}

export function useCrudOperations<T extends { id?: number | string }>(options: CrudOptions<T>) {
  const { queryKey, createFn, updateFn, deleteFn, entityName } = options
  const queryClient = useQueryClient()
  const toast = useToast()

  const createMutation = useMutation({
    mutationFn: createFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey })
      toast.success(`${entityName} created successfully`)
    },
    onError: (error: Error) => {
      toast.error(`Failed to create ${entityName}: ${error.message}`)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number | string; data: Partial<T> }) => updateFn(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey })
      toast.success(`${entityName} updated successfully`)
    },
    onError: (error: Error) => {
      toast.error(`Failed to update ${entityName}: ${error.message}`)
    },
  })

  const deleteMutation = deleteFn
    ? useMutation({
        mutationFn: deleteFn,
        onSuccess: () => {
          queryClient.invalidateQueries({ queryKey })
          toast.success(`${entityName} deleted successfully`)
        },
        onError: (error: Error) => {
          toast.error(`Failed to delete ${entityName}: ${error.message}`)
        },
      })
    : null

  const handleSave = async (data: Partial<T>, editingItem: Ref<T | null>) => {
    if (editingItem.value?.id) {
      await updateMutation.mutateAsync({ id: editingItem.value.id, data })
    } else {
      await createMutation.mutateAsync(data)
    }
    editingItem.value = null
  }

  const handleDelete = async (id: number | string) => {
    if (!deleteMutation) return
    await deleteMutation.mutateAsync(id)
  }

  return {
    createMutation,
    updateMutation,
    deleteMutation,
    handleSave,
    handleDelete,
    isLoading:
      createMutation.isPending || updateMutation.isPending || (deleteMutation?.isPending ?? false),
  }
}
