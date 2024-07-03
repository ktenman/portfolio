<template>
  <div class="container mt-3">
    <div class="row mt-4">
      <div class="col-md-6">
        <h3>Instruments</h3>
        <form @submit.prevent="saveInstrument">
          <div class="mb-3">
            <input
              id="symbol"
              v-model="newInstrument.symbol"
              class="form-control"
              placeholder="Symbol"
              required
            />
          </div>
          <div class="mb-3">
            <input id="name" v-model="newInstrument.name" class="form-control" placeholder="Name" />
          </div>
          <div class="mb-3">
            <input
              id="category"
              v-model="newInstrument.category"
              class="form-control"
              placeholder="Category"
            />
          </div>
          <div class="mb-3">
            <input
              id="currency"
              v-model="newInstrument.baseCurrency"
              class="form-control"
              placeholder="Base Currency"
              required
            />
          </div>
          <button class="btn btn-primary" type="submit">Save Instrument</button>
        </form>
        <div v-if="instruments.length > 0" class="mt-4">
          <h4>Saved Instruments</h4>
          <ul class="list-group">
            <li v-for="instrument in instruments" :key="instrument.id" class="list-group-item">
              <strong>{{ instrument.symbol }}</strong>
              {{ instrument.name ? '-' + instrument.name : '' }}
              {{ instrument.category ? '(' + instrument.category + ')' : '' }}
              [{{ instrument.baseCurrency }}]
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
const newInstrument = ref<Instrument>({ symbol: '', name: '', category: '', baseCurrency: '' })

const saveInstrument = async () => {
  try {
    const savedInstrument = await instrumentService.saveInstrument(newInstrument.value)
    instruments.value.push(savedInstrument)
    newInstrument.value = { symbol: '', name: '', category: '', baseCurrency: '' }
    alertType.value = AlertType.SUCCESS
    alertMessage.value = 'Instrument saved successfully.'
  } catch (error) {
    alertType.value = AlertType.ERROR
    alertMessage.value = 'Failed to save instrument. Please try again.'
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

onMounted(fetchInstruments)

const alertClass = computed(() => getAlertBootstrapClass(alertType.value))
</script>
