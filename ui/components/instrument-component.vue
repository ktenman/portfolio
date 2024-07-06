<template>
  <div class="container mt-3">
    <button class="btn btn-primary mb-3" id="addNewInstrument" @click="showAddInstrumentModal">
      Add New Instrument
    </button>

    <!-- Loading spinner -->
    <div v-if="isLoading" class="text-left my-5">
      <p class="mt-2">Loading instruments...</p>
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>

    <!-- Excel-like table for instruments -->
    <div v-else-if="instruments.length > 0" class="table-responsive">
      <table class="table table-striped table-hover">
        <thead>
          <tr>
            <th>Symbol</th>
            <th>Name</th>
            <th>Category</th>
            <th>Currency</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="instrument in instruments" :key="instrument.id">
            <td>{{ instrument.symbol }}</td>
            <td>{{ instrument.name }}</td>
            <td>{{ instrument.category }}</td>
            <td>{{ instrument.baseCurrency }}</td>
            <td>
              <button class="btn btn-sm btn-secondary me-2" @click="editInstrument(instrument)">
                Edit
              </button>
              <!--              <button class="btn btn-sm btn-danger" @click="deleteInstrument(instrument.id)">-->
              <!--                Delete-->
              <!--              </button>-->
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

    <div v-if="alertMessage || debugMessage" class="mt-3">
      <div :class="['alert', alertClass]" role="alert">
        <strong>{{ alertMessage }}</strong>
        <p v-if="debugMessage" class="mb-0 mt-2">
          <small>Debug: {{ debugMessage }}</small>
        </p>
        <ul v-if="Object.keys(validationErrors).length > 0" class="mt-2 mb-0">
          <li v-for="(error, field) in validationErrors" :key="field">{{ field }}: {{ error }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { Modal } from 'bootstrap'
import { InstrumentService } from '../services/instrument-service'
import { Instrument } from '../models/instrument'
import { AlertType, getAlertBootstrapClass } from '../models/alert-type'
import { ApiError } from '../models/api-error'

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
    alertType.value = AlertType.ERROR
    if (error instanceof ApiError) {
      alertMessage.value = error.message
      debugMessage.value = error.debugMessage
      validationErrors.value = error.validationErrors
    } else {
      alertMessage.value = `Failed to ${isEditing.value ? 'update' : 'save'} instrument. Please try again.`
      debugMessage.value = error instanceof Error ? error.message : 'Unknown error occurred'
      validationErrors.value = {}
    }
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
    alertType.value = AlertType.ERROR
    if (error instanceof ApiError) {
      alertMessage.value = error.message
      debugMessage.value = error.debugMessage
      validationErrors.value = error.validationErrors
    } else {
      alertMessage.value = 'Failed to load instruments. Please try again.'
      debugMessage.value = error instanceof Error ? error.message : 'Unknown error occurred'
      validationErrors.value = {}
    }
  } finally {
    isLoading.value = false
  }
}

const editInstrument = (instrument: Instrument) => {
  currentInstrument.value = { ...instrument }
  isEditing.value = true
  instrumentModal?.show()
}

// const deleteInstrument = async (id: number | undefined) => {
//   if (id === undefined) {
//     console.error('Attempted to delete an instrument with undefined id')
//     return
//   }
//
//   if (confirm('Are you sure you want to delete this instrument?')) {
//     try {
//       await instrumentService.deleteInstrument(id)
//       instruments.value = instruments.value.filter(i => i.id !== id)
//       alertType.value = AlertType.SUCCESS
//       alertMessage.value = 'Instrument deleted successfully.'
//       debugMessage.value = ''
//       validationErrors.value = {}
//     } catch (error) {
//       alertType.value = AlertType.ERROR
//       if (error instanceof ApiError) {
//         alertMessage.value = error.message
//         debugMessage.value = error.debugMessage
//         validationErrors.value = error.validationErrors
//       } else {
//         alertMessage.value = 'Failed to delete instrument. Please try again.'
//         debugMessage.value = error instanceof Error ? error.message : 'Unknown error occurred'
//         validationErrors.value = {}
//       }
//     }
//   }
// }

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
</style>
r
