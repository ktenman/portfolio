<template>
  <crud-layout
    :alert-message="alertMessage"
    :alert-type="alertType"
    :show-alert="showAlert"
    add-button-id="addNewTransaction"
    add-button-text="Add New Transaction"
    title="Transactions"
    @add="openAddModal"
  >
    <template #content>
      <transaction-table
        :is-loading="isLoading"
        :transactions="transactions"
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
import { onMounted } from 'vue'
import { useCrudPage } from '../../composables/use-crud-page'
import CrudLayout from '../shared/crud-layout.vue'
import TransactionTable from './transaction-table.vue'
import TransactionModal from './transaction-modal.vue'
import ConfirmDialog from '../shared/confirm-dialog.vue'
import { instrumentService, transactionService } from '../../services/service-registry'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'

const transactionCrud = useCrudPage<PortfolioTransaction>(transactionService, 'transactionModal')

const instrumentCrud = useCrudPage<Instrument>(instrumentService, '')

const {
  items: transactions,
  isLoading,
  fetchAll,
  selectedItem,
  showAlert,
  alertType,
  alertMessage,
  isConfirmOpen,
  confirmOptions,
  initModal,
  openAddModal,
  openEditModal,
  handleSave,
  handleDelete,
  handleConfirm,
  handleCancel,
} = transactionCrud

const { items: instruments, fetchAll: fetchInstruments } = instrumentCrud

onMounted(async () => {
  await Promise.all([fetchAll(), fetchInstruments()])
  initModal()
})
</script>
