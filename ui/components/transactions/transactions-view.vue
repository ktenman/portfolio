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
import { useCrudController } from '../../composables/use-crud-controller'
import CrudLayout from '../shared/crud-layout.vue'
import TransactionTable from './transaction-table.vue'
import TransactionModal from './transaction-modal.vue'
import ConfirmDialog from '../shared/confirm-dialog.vue'
import { instrumentService, transactionService } from '../../services'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'

const {
  items: transactions,
  isLoading,
  selectedItem,
  showAlert,
  alertType,
  alertMessage,
  isConfirmOpen,
  confirmOptions,
  openAddModal,
  openEditModal,
  handleSave,
  handleDelete,
  handleConfirm,
  handleCancel,
} = useCrudController<PortfolioTransaction>({
  service: transactionService,
  modalId: 'transactionModal',
})

const instrumentsController = useCrudController<Instrument>({
  service: instrumentService,
})

const { items: instruments } = instrumentsController
</script>
