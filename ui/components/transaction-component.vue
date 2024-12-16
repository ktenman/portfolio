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

    <div v-if="isLoading" class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>

    <div v-else-if="transactions.length > 0" class="table-responsive">
      <table class="table table-striped table-hover">
        <thead>
          <tr>
            <th>Date</th>
            <th>Instrument</th>
            <th class="d-none d-md-table-cell">Quantity</th>
            <th class="d-none d-md-table-cell">Price</th>
            <th>Amount</th>
            <th>Profit/Loss</th>
            <th class="text-end">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="transaction in transactions" :key="transaction.id">
            <td>{{ formatDate(transaction.transactionDate) }}</td>
            <td>{{ getInstrumentSymbol(transaction.instrumentId) }}</td>
            <td class="d-none d-md-table-cell">{{ formatNumber(transaction.quantity) }}</td>
            <td class="d-none d-md-table-cell">{{ formatNumber(transaction.price) }}</td>
            <td :class="amountClass(transaction)">{{ formattedAmount(transaction) }}</td>
            <td :class="earningsClass(transaction)">{{ formattedEarnings(transaction) }}</td>
            <td class="text-end">
              <button class="btn btn-sm btn-secondary me-2" @click="editTransaction(transaction)">
                <font-awesome-icon icon="pencil-alt" />
                <span class="d-none d-md-inline ms-1">Edit</span>
              </button>
              <button
                class="btn btn-sm btn-danger d-none d-md-inline-block"
                @click="deleteTransaction(transaction.id)"
              >
                <font-awesome-icon icon="trash-alt" />
                <span class="ms-1">Delete</span>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="alert alert-info" role="alert">
      No transactions found. Add a new transaction to get started.
    </div>

    <!-- Modal for Add/Edit Transaction -->
    <div
      class="modal fade"
      id="transactionModal"
      tabindex="-1"
      aria-labelledby="transactionModalLabel"
      aria-hidden="true"
    >
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="transactionModalLabel">
              {{ isEditing ? 'Edit Transaction' : 'Add New Transaction' }}
            </h5>
            <button
              type="button"
              class="btn-close"
              data-bs-dismiss="modal"
              aria-label="Close"
            ></button>
          </div>
          <div class="modal-body">
            <form @submit.prevent="saveTransaction">
              <div class="mb-3">
                <label for="instrumentId" class="form-label">Instrument</label>
                <select
                  v-model="currentTransaction.instrumentId"
                  id="instrumentId"
                  class="form-select"
                  required
                >
                  <option value="" disabled selected>Select Instrument</option>
                  <option
                    v-for="instrument in instruments"
                    :key="instrument.id"
                    :value="instrument.id"
                  >
                    {{ instrument.symbol }} - {{ instrument.name }}
                  </option>
                </select>
              </div>
              <div class="mb-3">
                <label for="platform" class="form-label">Platform</label>
                <select
                  v-model="currentTransaction.platform"
                  id="platform"
                  class="form-select"
                  required
                >
                  <option value="" disabled selected>Select Platform</option>
                  <option
                    v-for="platform in Object.values(Platform)"
                    :key="platform"
                    :value="platform"
                  >
                    {{ platform }}
                  </option>
                </select>
              </div>
              <div class="mb-3">
                <label for="transactionType" class="form-label">Transaction Type</label>
                <select
                  v-model="currentTransaction.transactionType"
                  id="transactionType"
                  class="form-select"
                  required
                >
                  <option value="" disabled selected>Select Transaction Type</option>
                  <option value="BUY">Buy</option>
                  <option value="SELL">Sell</option>
                </select>
              </div>
              <div class="mb-3">
                <label for="quantity" class="form-label">Quantity</label>
                <input
                  v-model="currentTransaction.quantity"
                  type="number"
                  step="0.00000001"
                  class="form-control"
                  id="quantity"
                  placeholder="Enter quantity"
                  required
                />
              </div>
              <div class="mb-3">
                <label for="price" class="form-label">Price</label>
                <input
                  v-model="currentTransaction.price"
                  type="number"
                  step="0.01"
                  class="form-control"
                  id="price"
                  placeholder="Enter price"
                  required
                />
              </div>
              <div class="mb-3">
                <label for="transactionDate" class="form-label">Transaction Date</label>
                <input
                  v-model="currentTransaction.transactionDate"
                  type="date"
                  class="form-control"
                  id="transactionDate"
                  required
                />
              </div>
            </form>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="saveTransaction">
              {{ isEditing ? 'Update' : 'Save' }} Transaction
            </button>
          </div>
        </div>
      </div>
    </div>

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
import { Modal } from 'bootstrap'
import { TransactionService } from '../services/transaction-service.ts'
import { InstrumentService } from '../services/instrument-service'
import { PortfolioTransaction } from '../models/portfolio-transaction'
import { Instrument } from '../models/instrument'
import { AlertType, getAlertBootstrapClass } from '../models/alert-type'
import { ApiError } from '../models/api-error'
import AlertMessageComponent from './alert-message-component.vue'
import { Platform } from '../models/platform'

const debugMessage = ref('')
const validationErrors = ref<Record<string, string>>({})
const alertMessage = ref('')
const alertType = ref<AlertType | null>(null)
const transactionService = new TransactionService()
const instrumentService = new InstrumentService()
const transactions = ref<PortfolioTransaction[]>([])
const instruments = ref<Instrument[]>([])
const currentTransaction = ref<Partial<PortfolioTransaction>>({
  transactionDate: new Date().toISOString().split('T')[0],
})
const isEditing = ref(false)
const isLoading = ref(true)
let transactionModal: Modal | null = null

onMounted(() => {
  fetchTransactions()
  fetchInstruments()
  transactionModal = new Modal(document.getElementById('transactionModal')!)
})

const showAddTransactionModal = () => {
  isEditing.value = false
  resetCurrentTransaction()
  transactionModal?.show()
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
    transactionModal?.hide()
    resetCurrentTransaction()
    alertType.value = AlertType.SUCCESS
    alertMessage.value = `Transaction ${isEditing.value ? 'updated' : 'saved'} successfully.`
    debugMessage.value = ''
    validationErrors.value = {}
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
  transactionModal?.show()
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
    alertType.value = AlertType.SUCCESS
    alertMessage.value = 'Transaction deleted successfully.'
    debugMessage.value = ''
    validationErrors.value = {}
  } catch (error) {
    handleApiError(error)
  }
}

const resetCurrentTransaction = () => {
  currentTransaction.value = {
    transactionDate: new Date().toISOString().split('T')[0],
  }
  debugMessage.value = ''
  validationErrors.value = {}
  alertMessage.value = ''
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

const handleApiError = (error: unknown) => {
  alertType.value = AlertType.ERROR
  if (error instanceof ApiError) {
    alertMessage.value = error.message
    debugMessage.value = error.debugMessage
    validationErrors.value = error.validationErrors
  } else {
    alertMessage.value = error instanceof Error ? error.message : 'An unexpected error occurred'
    debugMessage.value = ''
    validationErrors.value = {}
  }
}

const getInstrumentSymbol = (instrumentId: number | undefined) => {
  if (instrumentId === undefined) return 'Unknown'
  const instrument = instruments.value.find(i => i.id === instrumentId)
  return instrument ? instrument.symbol : 'Unknown'
}

const formatNumber = (value: number | undefined): string => {
  if (value === undefined) return ''

  if (value < 1 && value > 0) {
    return value.toExponential(3).replace('e-', ' * 10^-')
  }

  const [integerPart] = value.toString().split('.')
  const integerDigits = integerPart.length

  if (integerDigits === 1) {
    return value.toFixed(3)
  } else {
    return value.toFixed(2)
  }
}

const formattedAmount = (transaction: PortfolioTransaction): string => {
  const amount = transaction.quantity * transaction.price
  const formattedAmount = amount.toFixed(2)
  return transaction.transactionType === 'BUY' ? `+${formattedAmount}` : `-${formattedAmount}`
}

const amountClass = (transaction: PortfolioTransaction): string => {
  return transaction.transactionType === 'BUY' ? 'text-success' : 'text-danger'
}

const formattedEarnings = (transaction: PortfolioTransaction): string => {
  const formattedAbsEarnings = Math.abs(transaction.profit).toFixed(2)
  return transaction.profit >= 0 ? `+${formattedAbsEarnings}` : `-${formattedAbsEarnings}`
}

const earningsClass = (transaction: PortfolioTransaction): string => {
  return transaction.profit >= 0 ? 'text-success' : 'text-danger'
}

const formatDate = (date: string): string => {
  const dateObj = new Date(date)

  const day = String(dateObj.getDate()).padStart(2, '0')
  const month = String(dateObj.getMonth() + 1).padStart(2, '0')
  const year = String(dateObj.getFullYear()).slice(-2)

  return `${day}.${month}.${year}`
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
