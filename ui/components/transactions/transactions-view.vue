<template>
  <crud-layout
    add-button-id="addNewTransaction"
    add-button-text="Add New Transaction"
    title="Transactions"
    @add="openAddModal"
  >
    <template #content>
      <transaction-table
        :is-loading="isLoading"
        :transactions="transactions || []"
        :instruments="instruments || []"
        @delete="handleDelete"
        @edit="openEditModal"
      />
    </template>

    <template #modals>
      <transaction-modal
        :instruments="instruments || []"
        :transaction="selectedItem || {}"
        @save="handleSave"
      />
    </template>
  </crud-layout>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { useToast } from 'vue-toastification'
import { useModal } from '../../composables/use-modal'
import { useConfirm } from '../../composables/use-confirm'
import CrudLayout from '../shared/crud-layout.vue'
import TransactionTable from './transaction-table.vue'
import TransactionModal from './transaction-modal.vue'
import { instrumentsService } from '../../services/instruments-service'
import { transactionsService } from '../../services/transactions-service'
import { PortfolioTransaction } from '../../models/portfolio-transaction'

const selectedItem = ref<PortfolioTransaction | null>(null)
const { show: showModal, hide: hideModal } = useModal('transactionModal')
const { confirm } = useConfirm()
const queryClient = useQueryClient()
const toast = useToast()

const { data: transactions, isLoading } = useQuery({
  queryKey: ['transactions'],
  queryFn: transactionsService.getAll,
})

const { data: instruments } = useQuery({
  queryKey: ['instruments'],
  queryFn: instrumentsService.getAll,
})

const saveMutation = useMutation({
  mutationFn: (data: Partial<PortfolioTransaction>) => {
    if (selectedItem.value?.id) {
      return transactionsService.update(selectedItem.value.id, data)
    }
    return transactionsService.create(data)
  },
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['transactions'] })
    toast.success(`Transaction ${selectedItem.value?.id ? 'updated' : 'created'} successfully`)
    hideModal()
    selectedItem.value = null
  },
  onError: (error: Error) => {
    toast.error(`Failed to save transaction: ${error.message}`)
  },
})

const deleteMutation = useMutation({
  mutationFn: transactionsService.delete,
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['transactions'] })
    toast.success('Transaction deleted successfully')
  },
  onError: (error: Error) => {
    toast.error(`Failed to delete transaction: ${error.message}`)
  },
})

const openAddModal = () => {
  selectedItem.value = null
  showModal()
}

const openEditModal = (transaction: PortfolioTransaction) => {
  selectedItem.value = { ...transaction }
  showModal()
}

const handleSave = (transaction: Partial<PortfolioTransaction>) => {
  saveMutation.mutate(transaction)
}

const handleDelete = async (id: number | string) => {
  const shouldDelete = await confirm({
    title: 'Delete Transaction',
    message: 'Are you sure you want to delete this transaction?',
    confirmText: 'Delete',
    cancelText: 'Cancel',
    confirmClass: 'btn-danger',
  })

  if (shouldDelete) {
    deleteMutation.mutate(id)
  }
}
</script>
