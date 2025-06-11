<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4 class="mb-0">Instruments</h4>
      <button class="btn btn-primary btn-sm" id="addNewInstrument" @click="openAddModal">
        Add New Instrument
      </button>
    </div>

    <instrument-table 
      :instruments="instruments || []" 
      :is-loading="isLoadingInstruments || isSaving" 
      @edit="openEditModal" 
    />

    <instrument-modal :instrument="selectedItem" @save="handleSaveInstrument" />

    <alert v-model="showAlert" :type="alertType" :message="alertMessage" :duration="5000" />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useApi } from '../../composables/use-api'
import { useCrudView } from '../../composables/use-crud-view'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import Alert from '../shared/alert.vue'
import { InstrumentService } from '../../services/instrument-service'
import { Instrument } from '../../models/instrument'

const instrumentService = new InstrumentService()

const {
  data: instruments,
  isLoading: isLoadingInstruments,
  execute: fetchInstruments,
} = useApi(() => instrumentService.getAllInstruments())

const { isLoading: isSaving, execute: saveInstrument } = useApi(
  (instrument: Partial<Instrument>) => {
    return instrument.id
      ? instrumentService.update(instrument.id, instrument)
      : instrumentService.create(instrument)
  }
)

const {
  selectedItem,
  showAlert,
  alertType,
  alertMessage,
  initModal,
  openAddModal,
  openEditModal,
  showSuccess,
  handleSave,
} = useCrudView<Instrument>('instrumentModal')

onMounted(async () => {
  initModal()
  await fetchInstruments()
})

const handleSaveInstrument = async (data: Partial<Instrument>) => {
  await handleSave(
    data,
    saveInstrument,
    async () => {
      showSuccess(data.id ? 'Instrument updated successfully' : 'Instrument created successfully')
      await fetchInstruments()
    }
  )
}
</script>