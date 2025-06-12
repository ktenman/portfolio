<template>
  <crud-layout
    :alert-message="alertMessage"
    :alert-type="alertType"
    :show-alert="showAlert"
    add-button-id="addNewInstrument"
    add-button-text="Add New Instrument"
    title="Instruments"
    @add="openAddModal"
  >
    <template #content>
      <instrument-table :instruments="instruments" :is-loading="isLoading" @edit="openEditModal" />
    </template>

    <template #modals>
      <instrument-modal :instrument="selectedItem || {}" @save="handleSave" />
    </template>
  </crud-layout>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useCrudPage } from '../../composables/use-crud-page'
import CrudLayout from '../shared/crud-layout.vue'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import { instrumentService } from '../../services/service-registry'
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
} = useCrudPage<Instrument>(instrumentService, 'instrumentModal')

onMounted(async () => {
  await fetchAll()
  initModal()
})
</script>
