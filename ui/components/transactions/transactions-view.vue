<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4 class="mb-0">Transactions</h4>
      <button class="btn btn-primary btn-sm" @click="() => openAddModal()">
        Add New Transaction
      </button>
    </div>

    <transaction-table
      :transactions="transactions"
      :instruments="instruments"
      :is-loading="isLoading"
      @edit="openEditModal"
      @delete="handleDelete"
    />

    <transaction-modal
      :transaction="selectedItem || {}"
      :instruments="instruments"
      @save="handleSave"
    />

    <alert v-model="showAlert" :type="alertType" :message="alertMessage" :duration="5000" />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useCrudPage } from '../../composables/use-crud-page'
import { useResourceCrud } from '../../composables/use-resource-crud'
import TransactionTable from './transaction-table.vue'
import TransactionModal from './transaction-modal.vue'
import Alert from '../shared/alert.vue'
import { TransactionService } from '../../services/transaction-service'
import { InstrumentService } from '../../services/instrument-service'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'

const instrumentService = new InstrumentService()

const {
  items: transactions,
  isLoading,
  fetchAll,
  selectedItem,
  showAlert,
  alertType,
  alertMessage,
  initModal,
  openAddModal,
  openEditModal,
  handleSave,
  handleDelete,
} = useCrudPage<PortfolioTransaction>(new TransactionService(), 'transactionModal', {
  transactionDate: new Date().toISOString().split('T')[0],
})

// Separate composable for instruments since they're read-only
const { items: instruments } = useResourceCrud<Instrument>(instrumentService, { immediate: true })

onMounted(async () => {
  await fetchAll()
  initModal()
})
</script>
