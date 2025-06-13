<template>
  <crud-layout
    add-button-id="addNewInstrument"
    add-button-text="Add New Instrument"
    title="Instruments"
    @add="openAddModal"
  >
    <template #content>
      <instrument-table
        :instruments="items || []"
        :is-loading="isLoading"
        :is-error="isError"
        :error-message="error?.message"
        @edit="openEditModal"
      />
    </template>

    <template #modals>
      <instrument-modal :instrument="selectedItem || {}" @save="onSave" />
    </template>
  </crud-layout>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { useToast } from 'vue-toastification'
import { useBootstrapModal } from '../../composables/use-bootstrap-modal'
import CrudLayout from '../shared/crud-layout.vue'
import InstrumentTable from './instrument-table.vue'
import InstrumentModal from './instrument-modal.vue'
import { instrumentsService } from '../../services/instruments-service'
import { Instrument } from '../../models/instrument'

const selectedItem = ref<Instrument | null>(null)
const { show: showModal, hide: hideModal } = useBootstrapModal('instrumentModal')
const queryClient = useQueryClient()
const toast = useToast()

const {
  data: items,
  isLoading,
  isError,
  error,
} = useQuery({
  queryKey: ['instruments'],
  queryFn: instrumentsService.getAll,
})

const saveMutation = useMutation({
  mutationFn: (data: Partial<Instrument>) => {
    if (selectedItem.value?.id) {
      return instrumentsService.update(selectedItem.value.id, data)
    }
    return instrumentsService.create(data)
  },
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['instruments'] })
    toast.success(`Instrument ${selectedItem.value?.id ? 'updated' : 'created'} successfully`)
    hideModal()
    selectedItem.value = null
  },
  onError: (error: Error) => {
    toast.error(`Failed to save instrument: ${error.message}`)
  },
})

const openAddModal = () => {
  selectedItem.value = null
  showModal()
}

const openEditModal = (instrument: Instrument) => {
  selectedItem.value = { ...instrument }
  showModal()
}

const onSave = (instrument: Partial<Instrument>) => {
  saveMutation.mutate(instrument)
}
</script>
