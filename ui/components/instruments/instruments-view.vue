<template>
  <div class="container mt-2">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h4 class="mb-0">Instruments</h4>
      <button class="btn btn-primary btn-sm" id="addNewInstrument" @click="openAddModal">
        Add New Instrument
      </button>
    </div>

    <instrument-table :instruments="instruments" :is-loading="isLoading" @edit="openEditModal" />

    <instrument-modal :instrument="selectedItem || {}" @save="handleSave" />

    <alert v-model="showAlert" :type="alertType" :message="alertMessage" :duration="5000" />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useCrudPage } from '../../composables/use-crud-page'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import Alert from '../shared/alert.vue'
import { InstrumentService } from '../../services/instrument-service'
import { Instrument } from '../../models/instrument'

const {
  items: instruments,
  isLoading,
  fetchAll,
  selectedItem,
  showAlert,
  alertType,
  alertMessage,
  initModal,
  openAddModal,
  openEditModal,
  handleSave,
} = useCrudPage<Instrument>(new InstrumentService(), 'instrumentModal')

onMounted(async () => {
  await fetchAll()
  initModal()
})
</script>
