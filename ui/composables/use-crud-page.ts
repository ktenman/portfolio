import { Ref } from 'vue'
import { useResourceCrud } from './use-resource-crud'
import { useCrudView } from './use-crud-view'

interface UseCrudPageReturn<T> {
  // From useResourceCrud
  items: Ref<T[]>
  selectedItem: Ref<Partial<T> | null>
  isLoading: Ref<boolean>
  isCreating: Ref<boolean>
  isUpdating: Ref<boolean>
  isDeleting: Ref<boolean>
  error: Ref<Error | null>
  fetchAll: () => Promise<void>
  select: (item: T | null) => void

  // From useCrudView
  showAlert: Ref<boolean>
  alertType: Ref<'success' | 'danger'>
  alertMessage: Ref<string>
  initModal: () => void
  openAddModal: (initialState?: Partial<T>) => void
  openEditModal: (item: T) => void

  // Combined handlers
  handleSave: (item: Partial<T>) => Promise<void>
  handleDelete: (id: number | string) => Promise<void>
}

export function useCrudPage<T extends { id?: number | string }>(
  service: any,
  modalId: string,
  initialState: Partial<T> = {}
): UseCrudPageReturn<T> {
  const crud = useResourceCrud<T>(service)
  const view = useCrudView<T>(modalId)

  const handleSave = async (item: Partial<T>) => {
    const isUpdate = !!item.id
    const action = isUpdate ? () => crud.update(item.id!, item) : () => crud.create(item)

    await view.handleSave(item, action, () =>
      view.showSuccess(isUpdate ? 'Updated successfully' : 'Created successfully')
    )
  }

  const handleDelete = async (id: number | string) => {
    await view.handleDelete(
      () => crud.remove(id),
      () => view.showSuccess('Deleted successfully'),
      'Are you sure you want to delete this item?'
    )
  }

  return {
    // From useResourceCrud
    items: crud.items,
    selectedItem: view.selectedItem,
    isLoading: crud.isLoading,
    isCreating: crud.isCreating,
    isUpdating: crud.isUpdating,
    isDeleting: crud.isDeleting,
    error: crud.error,
    fetchAll: crud.fetchAll,
    select: crud.select,

    // From useCrudView
    showAlert: view.showAlert,
    alertType: view.alertType,
    alertMessage: view.alertMessage,
    initModal: view.initModal,
    openAddModal: () => view.openAddModal(initialState),
    openEditModal: view.openEditModal,

    // Combined handlers
    handleSave,
    handleDelete,
  }
}
