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
      <instrument-table :instruments="items || []" :is-loading="isLoading" @edit="openEditModal" />
    </template>

    <template #modals>
      <instrument-modal :instrument="selectedItem || {}" @save="onSave" />
    </template>
  </crud-layout>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useBootstrapModal } from '../../composables/use-bootstrap-modal'
import { useCrudOperations } from '../../composables/use-crud-operations'
import { useCrudAlerts } from '../../composables/use-crud-alerts'
import CrudLayout from '../shared/crud-layout.vue'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import { instrumentsService } from '../../services/instruments-service'
import { Instrument } from '../../models/instrument'

const { showAlert, alertType, alertMessage } = useCrudAlerts()
const selectedItem = ref<Instrument | null>(null)
const { show: showModal, hide: hideModal } = useBootstrapModal('instrumentModal')

const { data: items, isLoading } = useQuery({
  queryKey: ['instruments'],
  queryFn: instrumentsService.getAll,
})

const { handleSave } = useCrudOperations<Instrument>({
  queryKey: ['instruments'],
  createFn: instrumentsService.create,
  updateFn: instrumentsService.update,
  entityName: 'Instrument',
})

const openAddModal = () => {
  selectedItem.value = null
  showModal()
}

const openEditModal = (instrument: Instrument) => {
  selectedItem.value = { ...instrument }
  showModal()
}

const onSave = async (instrument: Partial<Instrument>) => {
  await handleSave(instrument, selectedItem)
  hideModal()
}
</script>
