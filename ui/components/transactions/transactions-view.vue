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
        :transactions="transactions"
        :instruments="instruments"
        @delete="handleDelete"
        @edit="openEditModal"
      />
    </template>

    <template #modals>
      <transaction-modal
        :instruments="instruments"
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
import { ref, onMounted } from 'vue'
import { Modal } from 'bootstrap'
import { useCrud } from '../../composables/use-crud'
import { useConfirm } from '../../composables/use-confirm'
import { useCrudAlerts } from '../../composables/use-crud-alerts'
import CrudLayout from '../shared/crud-layout.vue'
import TransactionTable from './transaction-table.vue'
import TransactionModal from './transaction-modal.vue'
import ConfirmDialog from '../shared/confirm-dialog.vue'
import { instrumentService, transactionService } from '../../services'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'
import { MESSAGES } from '../../constants/ui-constants'

const {
  items: transactions,
  isLoading,
  error,
  fetchAll,
  create,
  update,
  remove,
} = useCrud<PortfolioTransaction>(transactionService)

const { items: instruments, fetchAll: fetchInstruments } = useCrud<Instrument>(instrumentService)
const { showAlert, alertType, alertMessage, showSuccess, showError } = useCrudAlerts()

const selectedItem = ref<Partial<PortfolioTransaction> | null>(null)
let modalInstance: Modal | null = null

const { isConfirmOpen, confirmOptions, confirm, handleConfirm, handleCancel } = useConfirm()

onMounted(async () => {
  await Promise.all([fetchAll(), fetchInstruments()])
  const modalEl = document.getElementById('transactionModal')
  if (modalEl) {
    modalInstance = new Modal(modalEl)
  }
})

const openAddModal = () => {
  selectedItem.value = {}
  modalInstance?.show()
}

const openEditModal = (transaction: PortfolioTransaction) => {
  selectedItem.value = { ...transaction }
  modalInstance?.show()
}

const handleSave = async (transaction: Partial<PortfolioTransaction>) => {
  try {
    if (transaction.id) {
      await update(transaction.id, transaction)
      showSuccess(MESSAGES.UPDATE_SUCCESS)
    } else {
      await create(transaction)
      showSuccess(MESSAGES.SAVE_SUCCESS)
    }
    modalInstance?.hide()
    selectedItem.value = null
  } catch (_err) {
    showError(error.value?.message || MESSAGES.GENERIC_ERROR)
  }
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
    try {
      await remove(id)
      showSuccess(MESSAGES.DELETE_SUCCESS)
    } catch (_err) {
      showError(error.value?.message || MESSAGES.GENERIC_ERROR)
    }
  }
}
</script>
