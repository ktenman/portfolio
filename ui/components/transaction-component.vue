<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4 class="mb-0">Transactions</h4>
      <button
        class="btn btn-primary btn-sm"
        id="addNewTransaction"
        @click="showAddTransactionModal"
      >
        Add New Transaction
      </button>
    </div>

    <LoadingSpinner v-if="isLoading" />

    <div v-else-if="transactions.length > 0">
      <DataTable :columns="tableColumns" :data="transactions" key-field="id">
        <template #cell-actions="{ row }">
          <button class="btn btn-sm btn-secondary me-2" @click="editTransaction(row)">
            <font-awesome-icon icon="pencil-alt" />
            <span class="d-none d-md-inline ms-1">Edit</span>
          </button>
          <button
            class="btn btn-sm btn-danger d-none d-md-inline-block"
            @click="deleteTransaction(row.id)"
          >
            <font-awesome-icon icon="trash-alt" />
            <span class="ms-1">Delete</span>
          </button>
        </template>
      </DataTable>
    </div>

    <div v-else class="alert alert-info" role="alert">
      No transactions found. Add a new transaction to get started.
    </div>

    <!-- Modal for Add/Edit Transaction -->
    <ModalWrapper
      modal-id="transactionModal"
      :title="isEditing ? 'Edit Transaction' : 'Add New Transaction'"
      :save-button-text="isEditing ? 'Update Transaction' : 'Save Transaction'"
      @save="saveTransaction"
    >
      <template #body>
        <TransactionForm
          :transaction="currentTransaction"
          :instruments="instruments"
          @update:transaction="currentTransaction = $event"
          @submit="saveTransaction"
        />
      </template>
    </ModalWrapper>

    <AlertMessageComponent
      :message="alertMessage"
      :alertClass="alertClass"
      :debugMessage="debugMessage"
      :validationErrors="validationErrors"
    />
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { TransactionService } from '../services/transaction-service.ts'
import { InstrumentService } from '../services/instrument-service'
import { PortfolioTransaction } from '../models/portfolio-transaction'
import { Instrument } from '../models/instrument'
import { getAlertBootstrapClass } from '../models/alert-type'
import AlertMessageComponent from './alert-message-component.vue'
import LoadingSpinner from './common/loading-spinner.vue'
import DataTable, { TableColumn } from './common/data-table.vue'
import ModalWrapper from './common/modal-wrapper.vue'
import TransactionForm from './forms/transaction-form.vue'
import { useModal } from '../composables/use-modal'
import { useApiErrorHandler } from '../composables/use-api-error-handler'
import { useFormatters } from '../composables/use-formatters'

const {
  alertMessage,
  debugMessage,
  validationErrors,
  alertType,
  handleApiError,
  clearError,
  setSuccess,
} = useApiErrorHandler()
const { formatNumber, formatDate, formatProfitLoss } = useFormatters()
const { showModal, hideModal } = useModal('transactionModal')

const transactionService = new TransactionService()
const instrumentService = new InstrumentService()
const transactions = ref<PortfolioTransaction[]>([])
const instruments = ref<Instrument[]>([])
const currentTransaction = ref<Partial<PortfolioTransaction>>({
  transactionDate: new Date().toISOString().split('T')[0],
})
const isEditing = ref(false)
const isLoading = ref(true)

const tableColumns = computed<TableColumn[]>(() => [
  { key: 'transactionDate', label: 'Date', formatter: value => formatDate(value) },
  { key: 'instrumentId', label: 'Instrument', formatter: value => getInstrumentSymbol(value) },
  {
    key: 'quantity',
    label: 'Quantity',
    headerClass: 'd-none d-md-table-cell',
    cellClass: 'd-none d-md-table-cell',
    formatter: value => formatNumber(value),
  },
  {
    key: 'price',
    label: 'Price',
    headerClass: 'd-none d-md-table-cell',
    cellClass: 'd-none d-md-table-cell',
    formatter: value => formatNumber(value),
  },
  {
    key: 'amount',
    label: 'Amount',
    formatter: (_, row) => formattedAmount(row),
    cellClass: row => amountClass(row),
  },
  {
    key: 'profit',
    label: 'Profit/Loss',
    formatter: (_, row) => formatProfitLoss(getTransactionProfit(row)),
    cellClass: row => profitClass(getTransactionProfit(row)),
  },
  {
    key: 'averageCost',
    label: 'Average Cost',
    headerClass: 'd-none d-md-table-cell',
    cellClass: 'd-none d-md-table-cell',
    formatter: value => formatNumber(value),
  },
  { key: 'actions', label: 'Actions', headerClass: 'text-end', cellClass: 'text-end' },
])

onMounted(() => {
  fetchTransactions()
  fetchInstruments()
})

const showAddTransactionModal = () => {
  isEditing.value = false
  resetCurrentTransaction()
  showModal()
}

const saveTransaction = async () => {
  try {
    if (!isValidTransaction(currentTransaction.value)) {
      throw new Error('Invalid transaction data')
    }

    let savedTransaction: PortfolioTransaction
    if (isEditing.value && currentTransaction.value.id) {
      savedTransaction = await transactionService.updateTransaction(
        currentTransaction.value.id,
        currentTransaction.value as PortfolioTransaction
      )
      const index = transactions.value.findIndex(t => t.id === savedTransaction.id)
      if (index !== -1) {
        transactions.value[index] = savedTransaction
      }
    } else {
      savedTransaction = await transactionService.saveTransaction(
        currentTransaction.value as PortfolioTransaction
      )
      transactions.value.push(savedTransaction)
    }
    hideModal()
    resetCurrentTransaction()
    setSuccess(`Transaction ${isEditing.value ? 'updated' : 'saved'} successfully.`)
  } catch (error) {
    handleApiError(error)
  }
}

const fetchTransactions = async () => {
  isLoading.value = true
  try {
    transactions.value = await transactionService.getAllTransactions()
  } catch (error) {
    handleApiError(error)
  } finally {
    isLoading.value = false
  }
}

const fetchInstruments = async () => {
  try {
    instruments.value = await instrumentService.getAllInstruments()
  } catch (error) {
    handleApiError(error)
  }
}

const editTransaction = (transaction: PortfolioTransaction) => {
  currentTransaction.value = { ...transaction }
  isEditing.value = true
  showModal()
}

const deleteTransaction = async (id: number | undefined) => {
  if (id === undefined) {
    console.error('Attempted to delete a transaction with undefined id')
    return
  }

  if (!confirm('Are you sure you want to delete this transaction?')) {
    return
  }

  try {
    await transactionService.deleteTransaction(id)
    transactions.value = transactions.value.filter(t => t.id !== id)
    setSuccess('Transaction deleted successfully.')
  } catch (error) {
    handleApiError(error)
  }
}

const resetCurrentTransaction = () => {
  currentTransaction.value = {
    transactionDate: new Date().toISOString().split('T')[0],
  }
  clearError()
}

const isValidTransaction = (
  transaction: Partial<PortfolioTransaction>
): transaction is PortfolioTransaction => {
  return (
    typeof transaction.instrumentId === 'number' &&
    (transaction.transactionType === 'BUY' || transaction.transactionType === 'SELL') &&
    typeof transaction.quantity === 'number' &&
    typeof transaction.price === 'number' &&
    typeof transaction.transactionDate === 'string'
  )
}

const getInstrumentSymbol = (instrumentId: number | undefined) => {
  if (instrumentId === undefined) return 'Unknown'
  const instrument = instruments.value.find(i => i.id === instrumentId)
  return instrument ? instrument.symbol : 'Unknown'
}

const formattedAmount = (transaction: PortfolioTransaction): string => {
  const amount = transaction.quantity * transaction.price
  const formattedAmount = amount.toFixed(2)
  return transaction.transactionType === 'BUY' ? `+${formattedAmount}` : `-${formattedAmount}`
}

const amountClass = (transaction: PortfolioTransaction): string => {
  return transaction.transactionType === 'BUY' ? 'text-success' : 'text-danger'
}

const getTransactionProfit = (transaction: PortfolioTransaction): number | null | undefined => {
  return transaction.transactionType === 'SELL'
    ? transaction.realizedProfit
    : transaction.unrealizedProfit
}

const profitClass = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return ''
  return value >= 0 ? 'text-success' : 'text-danger'
}

const alertClass = computed(() => getAlertBootstrapClass(alertType.value))
</script>

<style scoped>
.table {
  font-size: 0.9rem;
}

.table th,
.table td {
  vertical-align: middle;
}

@media (max-width: 767px) {
  .table {
    font-size: 2.8vw;
  }

  .btn-sm {
    padding: 0.25rem 0.5rem;
  }
}
</style>

