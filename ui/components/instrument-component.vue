<template>
  <div class="container mt-3">
    <div class="row mt-4">
      <div class="col-md-6">
        <h3>{{ isEditing ? 'Edit Instrument' : 'Add New Instrument' }}</h3>
        <form @submit.prevent="saveInstrument">
          <div class="mb-3">
            <input
              id="symbol"
              v-model="currentInstrument.symbol"
              class="form-control"
              placeholder="Symbol"
              required
            />
          </div>
          <div class="mb-3">
            <input id="name" v-model="currentInstrument.name" class="form-control" placeholder="Name" />
          </div>
          <div class="mb-3">
            <input
              id="category"
              v-model="currentInstrument.category"
              class="form-control"
              placeholder="Category"
            />
          </div>
          <div class="mb-3">
            <input
              id="currency"
              v-model="currentInstrument.baseCurrency"
              class="form-control"
              placeholder="Base Currency"
              required
            />
          </div>
          <button class="btn btn-primary" type="submit">
            {{ isEditing ? 'Update' : 'Save' }} Instrument
          </button>
          <button v-if="isEditing" class="btn btn-secondary ms-2" @click="cancelEdit">
            Cancel
          </button>
        </form>
        <div v-if="instruments.length > 0" class="mt-4">
          <h4>Saved Instruments</h4>
          <ul class="list-group">
            <li v-for="instrument in instruments" :key="instrument.id" class="list-group-item">
              <strong>{{ instrument.symbol }}</strong>
              {{ instrument.name ? '-' + instrument.name : '' }}
              {{ instrument.category ? '(' + instrument.category + ')' : '' }}
              [{{ instrument.baseCurrency }}]
              <button class="btn btn-sm btn-warning ms-2" @click="editInstrument(instrument)">
                Edit
              </button>
              <button class="btn btn-sm btn-danger ms-2" @click="deleteInstrument(instrument.id)">
                Delete
              </button>
            </li>
          </ul>
        </div>
        <div v-else-if="instruments.length === 0" class="mt-3">
          <div class="alert alert-info" role="alert">No instruments saved yet.</div>
        </div>
        <div v-if="alertMessage" class="mt-3">
          <div :class="['alert', alertClass]" role="alert">
            {{ alertMessage }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { InstrumentService } from '../services/instrument-service'
import { Instrument } from '../models/instrument'
import { AlertType, getAlertBootstrapClass } from '../models/alert-type'

const alertMessage = ref('')
const alertType = ref<AlertType | null>(null)
const instrumentService = new InstrumentService()
const instruments = ref<Instrument[]>([])
const currentInstrument = ref<Instrument>({ symbol: '', name: '', category: '', baseCurrency: '' })
const isEditing = ref(false)

const saveInstrument = async () => {
  try {
    let savedInstrument: Instrument
    if (isEditing.value) {
      savedInstrument = await instrumentService.updateInstrument(currentInstrument.value.id!, currentInstrument.value)
      const index = instruments.value.findIndex(i => i.id === savedInstrument.id)
      if (index !== -1) {
        instruments.value[index] = savedInstrument
      }
      isEditing.value = false
    } else {
      savedInstrument = await instrumentService.saveInstrument(currentInstrument.value)
      instruments.value.push(savedInstrument)
    }
    currentInstrument.value = { symbol: '', name: '', category: '', baseCurrency: '' }
    alertType.value = AlertType.SUCCESS
    alertMessage.value = `Instrument ${isEditing.value ? 'updated' : 'saved'} successfully.`
  } catch (error) {
    alertType.value = AlertType.ERROR
    alertMessage.value = `Failed to ${isEditing.value ? 'update' : 'save'} instrument. Please try again.`
  }
}

const fetchInstruments = async () => {
  try {
    instruments.value = await instrumentService.getAllInstruments()
  } catch (error) {
    alertType.value = AlertType.ERROR
    alertMessage.value = 'Failed to load instruments. Please try again.'
  }
}

const editInstrument = (instrument: Instrument) => {
  currentInstrument.value = { ...instrument }
  isEditing.value = true
}

const cancelEdit = () => {
  currentInstrument.value = { symbol: '', name: '', category: '', baseCurrency: '' }
  isEditing.value = false
}

const deleteInstrument = async (id: number) => {
  if (confirm('Are you sure you want to delete this instrument?')) {
    try {
      await instrumentService.deleteInstrument(id)
      instruments.value = instruments.value.filter(i => i.id !== id)
      alertType.value = AlertType.SUCCESS
      alertMessage.value = 'Instrument deleted successfully.'
    } catch (error) {
      alertType.value = AlertType.ERROR
      alertMessage.value = 'Failed to delete instrument. Please try again.'
    }
  }
}

onMounted(fetchInstruments)

const alertClass = computed(() => getAlertBootstrapClass(alertType.value))
</script>
