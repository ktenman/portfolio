<template>
  <crud-layout
    :alert-message="alertMessage"
    :alert-type="alertType"
    :show-alert="showAlert"
    add-button-id="addNewTransaction"
    add-button-text="Add New Transaction"
    title="Transactions"
    @add="openAddModal"
    @update:showAlert="showAlert = $event"
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

      <confirm-dialog
        v-model="isConfirmOpen"
        :cancel-text="confirmOptions.cancelText"
        :confirm-class="confirmOptions.confirmClass"
        :confirm-text="confirmOptions.confirmText"
        :message="confirmOptions.message"
        :title="confirmOptions.title"
        @cancel="handleCancel"
        @confirm="handleConfirm"
      />
    </template>
  </crud-layout>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useBootstrapModal } from '../../composables/use-bootstrap-modal'
import { useCrudOperations } from '../../composables/use-crud-operations'
import { useConfirm } from '../../composables/use-confirm'
import { useCrudAlerts } from '../../composables/use-crud-alerts'
import CrudLayout from '../shared/crud-layout.vue'
import TransactionTable from './transaction-table.vue'
import TransactionModal from './transaction-modal.vue'
import ConfirmDialog from '../shared/confirm-dialog.vue'
import { instrumentsService } from '../../services/instruments-service'
import { transactionsService } from '../../services/transactions-service'
import { PortfolioTransaction } from '../../models/portfolio-transaction'

const { showAlert, alertType, alertMessage } = useCrudAlerts()
const selectedItem = ref<PortfolioTransaction | null>(null)
const { show: showModal, hide: hideModal } = useBootstrapModal('transactionModal')
const { isConfirmOpen, confirmOptions, confirm, handleConfirm, handleCancel } = useConfirm()

const { data: transactions, isLoading } = useQuery({
  queryKey: ['transactions'],
  queryFn: transactionsService.getAll,
})

const { data: instruments } = useQuery({
  queryKey: ['instruments'],
  queryFn: instrumentsService.getAll,
})

const { handleSave: saveTransaction, handleDelete: deleteTransaction } =
  useCrudOperations<PortfolioTransaction>({
    queryKey: ['transactions'],
    createFn: transactionsService.create,
    updateFn: transactionsService.update,
    deleteFn: transactionsService.delete,
    entityName: 'Transaction',
  })

const openAddModal = () => {
  selectedItem.value = null
  showModal()
}

const openEditModal = (transaction: PortfolioTransaction) => {
  selectedItem.value = { ...transaction }
  showModal()
}

const handleSave = async (transaction: Partial<PortfolioTransaction>) => {
  await saveTransaction(transaction, selectedItem)
  hideModal()
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
    await deleteTransaction(id)
  }
}
</script>
