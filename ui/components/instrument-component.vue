<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4 class="mb-0">Instruments</h4>
      <button class="btn btn-primary btn-sm" id="addNewInstrument" @click="showAddInstrumentModal">
        Add New Instrument
      </button>
    </div>

    <div v-if="isLoading" class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>

    <!-- Excel-like table for instruments -->
    <div v-else-if="instruments.length > 0" class="table-responsive">
      <table class="table table-striped table-hover">
        <thead>
        <tr>
          <th>Symbol</th>
          <th>Name</th>
          <th>Currency</th>
          <th>XIRR Annual Return</th>
          <th>Invested</th>
          <th>Current Value</th>
          <th>Profit/Loss</th>
          <th class="text-end">Actions</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="instrument in instruments" :key="instrument.id">
          <td data-label="Symbol">{{ instrument.symbol }}</td>
          <td data-label="Name">{{ instrument.name }}</td>
          <td data-label="Currency">{{ instrument.baseCurrency }}</td>
          <td data-label="XIRR Annual Return">{{ formatPercentage(instrument.xirr) }}</td>
          <td data-label="Invested">{{ formatCurrency(instrument.totalInvestment) }}</td>
          <td data-label="Current Value">{{ formatCurrency(instrument.currentValue) }}</td>
          <td data-label="Profit/Loss" :class="amountClass(instrument)">
            {{ formattedAmount(instrument) }}
          </td>
          <td data-label="Actions" class="text-end">
            <button class="btn btn-sm btn-secondary" @click="editInstrument(instrument)">
              <font-awesome-icon icon="pencil-alt" />
              <span class="d-none d-md-inline ms-1">Edit</span>
            </button>
          </td>
        </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="alert alert-info" role="alert">
      No instruments found. Add a new instrument to get started.
    </div>

    <!-- Modal for Add/Edit Instrument -->
    <div
      class="modal fade"
      id="instrumentModal"
      tabindex="-1"
      aria-labelledby="instrumentModalLabel"
      aria-hidden="true"
    >
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="instrumentModalLabel">
              {{ isEditing ? 'Edit Instrument' : 'Add New Instrument' }}
            </h5>
            <button
              type="button"
              class="btn-close"
              data-bs-dismiss="modal"
              aria-label="Close"
            ></button>
          </div>
          <div class="modal-body">
            <form @submit.prevent="saveInstrument">
              <div class="mb-3">
                <label for="symbol" class="form-label">Symbol</label>
                <input
                  v-model="currentInstrument.symbol"
                  type="text"
                  class="form-control"
                  id="symbol"
                  placeholder="Enter instrument symbol"
                  required
                />
              </div>
              <div class="mb-3">
                <label for="name" class="form-label">Name</label>
                <input
                  v-model="currentInstrument.name"
                  type="text"
                  class="form-control"
                  id="name"
                  placeholder="Enter instrument name"
                  required
                />
              </div>
              <div class="mb-3">
                <label for="providerName" class="form-label">Data Provider</label>
                <select
                  v-model="currentInstrument.providerName"
                  id="providerName"
                  class="form-select"
                  required
                >
                  <option value="" disabled selected>Select Data Provider</option>
                  <option value="ALPHA_VANTAGE">Alpha Vantage</option>
                  <option value="BINANCE">Binance</option>
                </select>
              </div>
              <div class="mb-3">
                <label for="category" class="form-label">Category</label>
                <select
                  v-model="currentInstrument.category"
                  id="category"
                  class="form-select"
                  required
                >
                  <option value="" disabled selected>Select Instrument Category</option>
                  <option value="STOCK">Stock</option>
                  <option value="ETF">ETF</option>
                  <option value="MUTUAL_FUND">Mutual Fund</option>
                  <option value="BOND">Bond</option>
                  <option value="CRYPTO">Cryptocurrency</option>
                </select>
              </div>
              <div class="mb-3">
                <label for="currency" class="form-label">Currency</label>
                <select
                  v-model="currentInstrument.baseCurrency"
                  id="currency"
                  class="form-select"
                  required
                >
                  <option value="" disabled selected>Select Currency</option>
                  <option value="USD">USD</option>
                  <option value="EUR">EUR</option>
                  <option value="GBP">GBP</option>
                  <!-- Add more currency options as needed -->
                </select>
              </div>
            </form>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="saveInstrument">
              {{ isEditing ? 'Update' : 'Save' }} Instrument
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
import { InstrumentService } from '../services/instrument-service'
import { Instrument } from '../models/instrument'
import { AlertType, getAlertBootstrapClass } from '../models/alert-type'
import { ApiError } from '../models/api-error'
import AlertMessageComponent from './alert-message-component.vue'

const alertMessage = ref('')
const debugMessage = ref('')
const alertType = ref<AlertType | null>(null)
const validationErrors = ref<Record<string, string>>({})
const instrumentService = new InstrumentService()
const instruments = ref<Instrument[]>([])
const currentInstrument = ref<Partial<Instrument>>({})
const isEditing = ref(false)
const isLoading = ref(true)
let instrumentModal: Modal | null = null

onMounted(() => {
  fetchInstruments()
  instrumentModal = new Modal(document.getElementById('instrumentModal')!)
})

const showAddInstrumentModal = () => {
  isEditing.value = false
  resetCurrentInstrument()
  instrumentModal?.show()
}

const formatPercentage = (value: number) => `${(value * 100).toFixed(2)}%`

const formatCurrency = (value: number): string => {
  return `${Math.abs(value).toFixed(2)}`
}

const formattedAmount = (instrument: Instrument): string => {
  if (instrument.profit === 0) {
    return '0.00'
  }
  const formattedAmount = formatCurrency(instrument.profit)
  return instrument.profit > 0 ? `+${formattedAmount}` : `-${formattedAmount}`
}

const amountClass = (instrument: Instrument): string => {
  return instrument.profit >= 0 ? 'text-success' : 'text-danger'
}

const saveInstrument = async () => {
  try {
    if (!isValidInstrument(currentInstrument.value)) {
      throw new Error('Invalid instrument data')
    }

    let savedInstrument: Instrument
    if (isEditing.value && currentInstrument.value.id) {
      savedInstrument = await instrumentService.updateInstrument(
        currentInstrument.value.id,
        currentInstrument.value as Instrument
      )
      const index = instruments.value.findIndex(i => i.id === savedInstrument.id)
      if (index !== -1) {
        instruments.value[index] = savedInstrument
      }
    } else {
      savedInstrument = await instrumentService.saveInstrument(
        currentInstrument.value as Instrument
      )
      instruments.value.push(savedInstrument)
    }
    instrumentModal?.hide()
    resetCurrentInstrument()
    alertType.value = AlertType.SUCCESS
    alertMessage.value = `Instrument ${isEditing.value ? 'updated' : 'saved'} successfully.`
    debugMessage.value = ''
    validationErrors.value = {}
  } catch (error) {
    handleApiError(error)
  }
}

const fetchInstruments = async () => {
  isLoading.value = true
  try {
    instruments.value = await instrumentService.getAllInstruments()
    alertMessage.value = ''
    debugMessage.value = ''
    validationErrors.value = {}
  } catch (error) {
    handleApiError(error)
  } finally {
    isLoading.value = false
  }
}

const handleApiError = (error: unknown) => {
  alertType.value = AlertType.ERROR
  if (error instanceof ApiError) {
    alertMessage.value = error.message
    debugMessage.value = error.debugMessage
    validationErrors.value = error.validationErrors
  } else {
    alertMessage.value = 'An unexpected error occurred. Please try again.'
    debugMessage.value = error instanceof Error ? error.message : 'Unknown error'
    validationErrors.value = {}
  }
}

const editInstrument = (instrument: Instrument) => {
  currentInstrument.value = { ...instrument }
  isEditing.value = true
  instrumentModal?.show()
}

const resetCurrentInstrument = () => {
  currentInstrument.value = {}
}

const isValidInstrument = (instrument: Partial<Instrument>): instrument is Instrument => {
  return (
    typeof instrument.symbol === 'string' &&
    typeof instrument.name === 'string' &&
    typeof instrument.category === 'string' &&
    typeof instrument.baseCurrency === 'string'
  )
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
    display: block;
    width: 100%;
    overflow: hidden;
  }

  .table thead {
    display: none;
  }

  .table tbody, .table tr, .table td {
    display: block;
    width: 100%;
  }

  .table tr {
    margin-bottom: 1rem;
    border-bottom: 1px solid #dee2e6;
  }

  .table td {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.5rem;
    font-size: 1rem;
    text-align: left;
  }

  .table td[data-label]:before {
    content: attr(data-label);
    font-weight: bold;
    color: #6c757d;
    margin-right: 0.5rem;
    width: 50%;
    flex-shrink: 0;
  }

  .table td.text-end {
    justify-content: flex-end;
  }
}
</style>
