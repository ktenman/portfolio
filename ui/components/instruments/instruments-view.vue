<template>
  <crud-layout
    :alert-message="alertMessage"
    :alert-type="alertType"
    :show-alert="showAlert"
    add-button-id="addNewInstrument"
    add-button-text="Add New Instrument"
    title="Instruments"
    @add="openAddModal"
    @update:showAlert="showAlert = $event"
  >
    <template #content>
      <instrument-table :instruments="items" :is-loading="isLoading" @edit="openEditModal" />
    </template>

    <template #modals>
      <instrument-modal :instrument="selectedItem || {}" @save="handleSave" />
    </template>
  </crud-layout>
</template>

<script setup lang="ts">
import { useCrudController } from '../../composables/use-crud-controller'
import CrudLayout from '../shared/crud-layout.vue'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import { instrumentService } from '../../services'
import { Instrument } from '../../models/instrument'

const {
  items,
  isLoading,
  selectedItem,
  showAlert,
  alertType,
  alertMessage,
  openAddModal,
  openEditModal,
  handleSave,
} = useCrudController<Instrument>({
  service: instrumentService,
  modalId: 'instrumentModal',
})
</script>
