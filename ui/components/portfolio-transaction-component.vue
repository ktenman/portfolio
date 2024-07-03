<template>
  <div class="container mt-3">
    <h3 class="mb-4">Add New Transaction</h3>
    <form @submit.prevent="saveTransaction" class="mb-5">
      <div class="mb-3">
        <label for="instrumentId" class="form-label">Instrument</label>
        <select
          v-model="currentTransaction.instrumentId"
          id="instrumentId"
          class="form-select"
          required
        >
          <option value="" disabled>Select Instrument</option>
          <option v-for="instrument in instruments" :key="instrument.id" :value="instrument.id">
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
          <option value="" disabled>Select Transaction Type</option>
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
      <button class="btn btn-primary" type="submit">
        {{ isEditing ? 'Update' : 'Save' }} Transaction
      </button>
      <button v-if="isEditing" class="btn btn-secondary ms-2" @click="cancelEdit">Cancel</button>
    </form>

    <h3 class="mb-4">Saved Transactions</h3>
    <div v-if="transactions.length > 0">
      <div v-for="transaction in transactions" :key="transaction.id" class="card mb-3">
        <div class="card-body d-flex justify-content-between align-items-center">
          <div>
            <strong>{{ getInstrumentSymbol(transaction.instrumentId) }}</strong>
            {{ transaction.transactionType }} {{ transaction.quantity }} @
            {{ transaction.price }} on {{ formatDate(transaction.transactionDate) }}
          </div>
          <div>
            <button class="btn btn-sm btn-warning me-2" @click="editTransaction(transaction)">
              Edit
            </button>
            <button class="btn btn-sm btn-danger" @click="deleteTransaction(transaction.id)">
              Delete
            </button>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="alert alert-info" role="alert">No transactions saved yet.</div>

    <div v-if="alertMessage" class="mt-3">
      <div :class="['alert', alertClass]" role="alert">
        {{ alertMessage }}
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
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
const currentTransaction = ref<PortfolioTransaction>({
  instrumentId: 0,
  transactionType: 'BUY',
  quantity: 0,
  price: 0,
  transactionDate: new Date().toISOString().split('T')[0],
})
const isEditing = ref(false)

const saveTransaction = async () => {
  try {
    let savedTransaction: PortfolioTransaction
    if (isEditing.value) {
      savedTransaction = await transactionService.updateTransaction(
        currentTransaction.value.id!,
        currentTransaction.value
      )
      const index = transactions.value.findIndex(t => t.id === savedTransaction.id)
      if (index !== -1) {
        transactions.value[index] = savedTransaction
      }
      isEditing.value = false
    } else {
      savedTransaction = await transactionService.saveTransaction(currentTransaction.value)
      transactions.value.push(savedTransaction)
    }
    resetCurrentTransaction()
    alertType.value = AlertType.SUCCESS
    alertMessage.value = `Transaction ${isEditing.value ? 'updated' : 'saved'} successfully.`
  } catch (error) {
    alertType.value = AlertType.ERROR
    alertMessage.value = `Failed to ${isEditing.value ? 'update' : 'save'} transaction. Please try again.`
  }
}

const fetchTransactions = async () => {
  try {
    transactions.value = await transactionService.getAllTransactions()
  } catch (error) {
    alertType.value = AlertType.ERROR
    alertMessage.value = 'Failed to load transactions. Please try again.'
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
}

const cancelEdit = () => {
  resetCurrentTransaction()
  isEditing.value = false
}

const deleteTransaction = async (id: number | undefined) => {
  if (id === undefined) {
    console.error('Attempted to delete a transaction with undefined id')
    return
  }

  if (confirm('Are you sure you want to delete this transaction?')) {
    try {
      await transactionService.deleteTransaction(id)
      transactions.value = transactions.value.filter(t => t.id !== id)
      alertType.value = AlertType.SUCCESS
      alertMessage.value = 'Transaction deleted successfully.'
    } catch (error) {
      alertType.value = AlertType.ERROR
      alertMessage.value = 'Failed to delete transaction. Please try again.'
    }
  }
}

const resetCurrentTransaction = () => {
  currentTransaction.value = {
    instrumentId: 0,
    transactionType: 'BUY',
    quantity: 0,
    price: 0,
    transactionDate: new Date().toISOString().split('T')[0],
  }
}

const getInstrumentSymbol = (instrumentId: number) => {
  const instrument = instruments.value.find(i => i.id === instrumentId)
  return instrument ? instrument.symbol : 'Unknown'
}

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleDateString()
}

onMounted(() => {
  fetchTransactions()
  fetchInstruments()
})

const alertClass = computed(() => getAlertBootstrapClass(alertType.value))
</script>

<style scoped>
.form-label {
  font-weight: bold;
}
</style>
