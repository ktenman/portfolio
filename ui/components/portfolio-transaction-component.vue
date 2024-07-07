<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4 class="mb-0">Transactions</h4>
      <button class="btn btn-primary btn-sm" @click="showAddTransactionModal">
        Add New Transaction
      </button>
    </div>

    <!-- Loading spinner -->
    <div v-if="isLoading" class="text-left my-5">
      <p class="mt-2">Loading transactions...</p>
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <!-- Excel-like table for transactions -->
    <div v-else-if="transactions.length > 0" class="table-responsive">
      <table class="table table-striped table-hover">
        <thead>
          <tr>
            <th>Instrument</th>
            <th>Type</th>
            <th>Quantity</th>
            <th>Price</th>
            <th>Date</th>
            <th class="text-end">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="transaction in transactions" :key="transaction.id">
            <td>{{ getInstrumentSymbol(transaction.instrumentId) }}</td>
            <td>{{ transaction.transactionType }}</td>
            <td>{{ formatNumber(transaction.quantity) }}</td>
            <td>{{ formatNumber(transaction.price) }}</td>
            <td>{{ formatDate(transaction.transactionDate) }}</td>
            <td class="text-end">
              <button class="btn btn-sm btn-secondary me-2" @click="editTransaction(transaction)">
                <font-awesome-icon icon="pencil-alt" />
                <span class="d-none d-md-inline ms-1">Edit</span>
              </button>
              <!--              <button class="btn btn-sm btn-danger" @click="deleteTransaction(transaction.id)">-->
              <!--                Delete-->
              <!--              </button>-->
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
            <button
              type="button"
              class="btn btn-primary"
              :disabled="!isValidTransaction(currentTransaction.value)"
              @click="saveTransaction"
            >
              {{ isEditing ? 'Update' : 'Save' }} Transaction
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="alertMessage" class="mt-3">
      <div :class="['alert', alertClass]" role="alert">
        {{ alertMessage }}
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { Modal } from 'bootstrap'
import { PortfolioTransactionService } from '../services/portfolio-transaction-service'
import { InstrumentService } from '../services/instrument-service'
import { PortfolioTransaction } from '../models/portfolio-transaction'
import { Instrument } from '../models/instrument'
import { AlertType, getAlertBootstrapClass } from '../models/alert-type'

const alertMessage = ref('')
const alertType = ref<AlertType | null>(null)
const transactionService = new PortfolioTransactionService()
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
  } catch (error) {
    alertType.value = AlertType.ERROR
    alertMessage.value = `Failed to ${isEditing.value ? 'update' : 'save'} transaction. Please try again.`
  }
}

const fetchTransactions = async () => {
  isLoading.value = true
  try {
    transactions.value = await transactionService.getAllTransactions()
  } catch (error) {
    alertType.value = AlertType.ERROR
    alertMessage.value = 'Failed to load transactions. Please try again.'
  } finally {
    isLoading.value = false
  }
}

const fetchInstruments = async () => {
  try {
    instruments.value = await instrumentService.getAllInstruments()
  } catch (error) {
    alertType.value = AlertType.ERROR
    alertMessage.value = 'Failed to load instruments. Please try again.'
  }
}

const editTransaction = (transaction: PortfolioTransaction) => {
  currentTransaction.value = { ...transaction }
  isEditing.value = true
  transactionModal?.show()
}

// const deleteTransaction = async (id: number | undefined) => {
//   if (id === undefined) {
//     console.error('Attempted to delete a transaction with undefined id')
//     return
//   }
//
//   if (confirm('Are you sure you want to delete this transaction?')) {
//     try {
//       await transactionService.deleteTransaction(id)
//       transactions.value = transactions.value.filter(t => t.id !== id)
//       alertType.value = AlertType.SUCCESS
//       alertMessage.value = 'Transaction deleted successfully.'
//     } catch (error) {
//       alertType.value = AlertType.ERROR
//       alertMessage.value = 'Failed to delete transaction. Please try again.'
//     }
//   }
// }

const resetCurrentTransaction = () => {
  currentTransaction.value = {
    transactionDate: new Date().toISOString().split('T')[0],
  }
}

const isValidTransaction = (
  transaction: Partial<PortfolioTransaction>
): transaction is PortfolioTransaction => {
  return (
    typeof transaction.instrumentId === 'number' &&
    (transaction.transactionType === 'BUY' || transaction.transactionType === 'SELL') &&
    typeof transaction.quantity === 'number' &&
    typeof transaction.price === 'number' &&
    typeof transaction.transactionDate === 'string' &&
    transaction.instrumentId !== undefined &&
    transaction.transactionType !== undefined &&
    transaction.quantity !== undefined &&
    transaction.price !== undefined &&
    transaction.transactionDate.trim() !== ''
  )
}

const getInstrumentSymbol = (instrumentId: number | undefined) => {
  if (instrumentId === undefined) return 'Unknown'
  const instrument = instruments.value.find(i => i.id === instrumentId)
  return instrument ? instrument.symbol : 'Unknown'
}

const formatNumber = (value: number | undefined): string => {
  if (value === undefined) return ''
  return value.toFixed(2)
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
