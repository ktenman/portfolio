<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4 class="mb-0">Instruments</h4>
      <button class="btn btn-primary btn-sm" id="addNewInstrument" @click="showAddInstrumentModal">
        Add New Instrument
      </button>
    </div>

    <LoadingSpinner v-if="isLoading" />

    <!-- Excel-like table for instruments -->
    <div v-else-if="instruments.length > 0">
      <InstrumentTable :instruments="instruments" @edit="editInstrument" />
    </div>

    <div v-else class="alert alert-info" role="alert">
      No instruments found. Add a new instrument to get started.
    </div>

    <!-- Modal for Add/Edit Instrument -->
    <ModalWrapper
      modal-id="instrumentModal"
      :title="isEditing ? 'Edit Instrument' : 'Add New Instrument'"
      :save-button-text="isEditing ? 'Update Instrument' : 'Save Instrument'"
      @save="saveInstrument"
    >
      <template #body>
        <InstrumentForm
          :instrument="currentInstrument"
          @update:instrument="currentInstrument = $event"
          @submit="saveInstrument"
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
import { InstrumentService } from '../services/instrument-service'
import { Instrument } from '../models/instrument'
import { getAlertBootstrapClass } from '../models/alert-type'
import AlertMessageComponent from './alert-message-component.vue'
import LoadingSpinner from './common/loading-spinner.vue'
import ModalWrapper from './common/modal-wrapper.vue'
import InstrumentForm from './forms/instrument-form.vue'
import InstrumentTable from './tables/instrument-table.vue'
import { useModal } from '../composables/use-modal'
import { useApiErrorHandler } from '../composables/use-api-error-handler'

const {
  alertMessage,
  debugMessage,
  validationErrors,
  alertType,
  handleApiError,
  clearError,
  setSuccess,
} = useApiErrorHandler()
const { showModal, hideModal } = useModal('instrumentModal')

const instrumentService = new InstrumentService()
const instruments = ref<Instrument[]>([])
const currentInstrument = ref<Partial<Instrument>>({})
const isEditing = ref(false)
const isLoading = ref(true)

onMounted(() => {
  fetchInstruments()
})

const showAddInstrumentModal = () => {
  isEditing.value = false
  resetCurrentInstrument()
  showModal()
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
    hideModal()
    resetCurrentInstrument()
    setSuccess(`Instrument ${isEditing.value ? 'updated' : 'saved'} successfully.`)
  } catch (error) {
    handleApiError(error)
  }
}

const fetchInstruments = async () => {
  isLoading.value = true
  try {
    instruments.value = await instrumentService.getAllInstruments()
    clearError()
  } catch (error) {
    handleApiError(error)
  } finally {
    isLoading.value = false
  }
}

const editInstrument = (instrument: Instrument) => {
  currentInstrument.value = { ...instrument }
  isEditing.value = true
  showModal()
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
/* Component-specific styles only - common styles are in common.css */
</style>
