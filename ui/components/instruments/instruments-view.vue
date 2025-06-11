<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4 class="mb-0">Instruments</h4>
      <button class="btn btn-primary btn-sm" id="addNewInstrument" @click="openAddModal">
        Add New Instrument
      </button>
    </div>

    <instrument-table :instruments="instruments" :is-loading="isLoading" @edit="openEditModal" />

    <instrument-modal :instrument="selectedInstrument" @save="handleSave" />

    <alert v-model="showAlert" :type="alertType" :message="alertMessage" :duration="5000" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Modal } from 'bootstrap'
import { useResourceCrud } from '../../composables/use-resource-crud'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import Alert from '../shared/alert.vue'
import { InstrumentService } from '../../services/instrument-service'
import { Instrument } from '../../models/instrument'
import { ApiError } from '../../models/api-error'

const instrumentService = new InstrumentService()

// CRUD operations for instruments
const {
  items: instruments,
  isLoading,
  fetchAll: fetchInstruments,
  create: createInstrument,
  update: updateInstrument,
} = useResourceCrud<Instrument>(instrumentService)

// Modal management
let instrumentModal: Modal | null = null

// Local state
const selectedInstrument = ref<Partial<Instrument>>({})
const showAlert = ref(false)
const alertType = ref<'success' | 'danger'>('success')
const alertMessage = ref('')

// Initialize data
onMounted(async () => {
  await fetchInstruments()
  instrumentModal = new Modal(document.getElementById('instrumentModal')!)
})

const openAddModal = () => {
  selectedInstrument.value = {}
  instrumentModal?.show()
}

const openEditModal = (instrument: Instrument) => {
  selectedInstrument.value = { ...instrument }
  instrumentModal?.show()
}

const handleSave = async (data: Partial<Instrument>) => {
  try {
    if (data.id) {
      await updateInstrument(data.id, data)
      showSuccess('Instrument updated successfully')
    } else {
      await createInstrument(data)
      showSuccess('Instrument created successfully')
    }
    instrumentModal?.hide()
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
