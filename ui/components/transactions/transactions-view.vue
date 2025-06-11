<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4 class="mb-0">Transactions</h4>
      <button
        class="btn btn-primary btn-sm"
        id="addNewTransaction"
        @click="openAddModal"
      >
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
      :transaction="selectedTransaction"
      :instruments="instruments"
      @save="handleSave"
    />

    <alert
      v-model="showAlert"
      :type="alertType"
      :message="alertMessage"
      :duration="5000"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Modal } from 'bootstrap'
import { useResourceCrud } from '../../composables/use-resource-crud'
import TransactionTable from './transaction-table.vue'
import TransactionModal from './transaction-modal.vue'
import Alert from '../shared/alert.vue'
import { TransactionService } from '../../services/transaction-service'
import { InstrumentService } from '../../services/instrument-service'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'
import { ApiError } from '../../models/api-error'

const transactionService = new TransactionService()
const instrumentService = new InstrumentService()

// CRUD operations for transactions
const {
  items: transactions,
  isLoading,
  fetchAll: fetchTransactions,
  create: createTransaction,
  update: updateTransaction,
  remove: deleteTransaction
} = useResourceCrud<PortfolioTransaction>(transactionService)

// Modal management
let transactionModal: Modal | null = null

// Local state
const instruments = ref<Instrument[]>([])
const selectedTransaction = ref<Partial<PortfolioTransaction>>({
  transactionDate: new Date().toISOString().split('T')[0]
})
const showAlert = ref(false)
const alertType = ref<'success' | 'danger'>('success')
const alertMessage = ref('')

// Initialize data
onMounted(async () => {
  await fetchTransactions()
  await fetchInstruments()
  transactionModal = new Modal(document.getElementById('transactionModal')!)
})

const fetchInstruments = async () => {
  try {
    instruments.value = await instrumentService.getAllInstruments()
  } catch (error) {
    showError(error)
  }
}

const openAddModal = () => {
  selectedTransaction.value = {
    transactionDate: new Date().toISOString().split('T')[0]
  }
  transactionModal?.show()
}

const openEditModal = (transaction: PortfolioTransaction) => {
  selectedTransaction.value = { ...transaction }
  transactionModal?.show()
}

const handleSave = async (data: Partial<PortfolioTransaction>) => {
  try {
    if (data.id) {
      await updateTransaction(data.id, data)
      showSuccess('Transaction updated successfully')
    } else {
      await createTransaction(data)
      showSuccess('Transaction created successfully')
    }
    transactionModal?.hide()
  } catch (error) {
    showError(error)
  }
}

const handleDelete = async (id: number) => {
  if (!confirm('Are you sure you want to delete this transaction?')) {
    return
  }

  try {
    await deleteTransaction(id)
    showSuccess('Transaction deleted successfully')
  } catch (error) {
    showError(error)
  }
}

const showSuccess = (message: string) => {
  alertType.value = 'success'
  alertMessage.value = message
  showAlert.value = true
}

const showError = (error: unknown) => {
  alertType.value = 'danger'
  if (error instanceof ApiError) {
    alertMessage.value = error.message
  } else {
    alertMessage.value = error instanceof Error ? error.message : 'An unexpected error occurred'
  }
  showAlert.value = true
}
</script>

