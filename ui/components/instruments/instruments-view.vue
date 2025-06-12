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
import { ref, onMounted } from 'vue'
import { Modal } from 'bootstrap'
import { useCrud } from '../../composables/use-crud'
import { useCrudAlerts } from '../../composables/use-crud-alerts'
import CrudLayout from '../shared/crud-layout.vue'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import { instrumentService } from '../../services'
import { Instrument } from '../../models/instrument'
import { MESSAGES } from '../../constants/ui-constants'

const { items, isLoading, error, fetchAll, create, update } = useCrud<Instrument>(instrumentService)
const { showAlert, alertType, alertMessage, showSuccess, showError } = useCrudAlerts()

const selectedItem = ref<Partial<Instrument> | null>(null)
let modalInstance: Modal | null = null

onMounted(async () => {
  await fetchAll()
  const modalEl = document.getElementById('instrumentModal')
  if (modalEl) {
    modalInstance = new Modal(modalEl)
  }
})

const openAddModal = () => {
  selectedItem.value = {}
  modalInstance?.show()
}

const openEditModal = (instrument: Instrument) => {
  selectedItem.value = { ...instrument }
  modalInstance?.show()
}

const handleSave = async (instrument: Partial<Instrument>) => {
  try {
    if (instrument.id) {
      await update(instrument.id, instrument)
      showSuccess(MESSAGES.UPDATE_SUCCESS)
    } else {
      await create(instrument)
      showSuccess(MESSAGES.SAVE_SUCCESS)
    }
    modalInstance?.hide()
    selectedItem.value = null
  } catch (_err) {
    showError(error.value?.message || MESSAGES.GENERIC_ERROR)
  }
}
</script>
