<template>
  <form @submit.prevent="handleSubmit">
    <div class="mb-3">
      <label for="symbol" class="form-label">Symbol</label>
      <input
        v-model="localInstrument.symbol"
        type="text"
        class="form-control"
        id="symbol"
        placeholder="Enter instrument symbol"
        required
      />
    </div>
    <div class="mb-3">
      <label for="name" class="form-label">Name</label>
      <input
        v-model="localInstrument.name"
        type="text"
        class="form-control"
        id="name"
        placeholder="Enter instrument name"
        required
      />
    </div>
    <div class="mb-3">
      <label for="providerName" class="form-label">Data Provider</label>
      <select v-model="localInstrument.providerName" id="providerName" class="form-select" required>
        <option value="" disabled selected>Select Data Provider</option>
        <option v-for="provider in providerNames" :key="provider" :value="provider">
          {{ PROVIDER_NAME_DISPLAY[provider] }}
        </option>
      </select>
    </div>
    <div class="mb-3">
      <label for="category" class="form-label">Category</label>
      <select v-model="localInstrument.category" id="category" class="form-select" required>
        <option value="" disabled selected>Select Instrument Category</option>
        <option value="STOCK">Stock</option>
        <option value="ETF">ETF</option>
        <option value="MUTUAL_FUND">Mutual Fund</option>
        <option value="BOND">Bond</option>
        <option value="CRYPTO">Cryptocurrency</option>
      </select>
    </div>
    <div class="mb-3">
      <label for="currency" class="form-label">Currency</label>
      <select v-model="localInstrument.baseCurrency" id="currency" class="form-select" required>
        <option value="" disabled selected>Select Currency</option>
        <option value="USD">USD</option>
        <option value="EUR">EUR</option>
        <option value="GBP">GBP</option>
      </select>
    </div>
  </form>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue'
import { Instrument } from '../../models/instrument'
import { PROVIDER_NAME_DISPLAY, ProviderName } from '../../constants/provider-name'

interface Props {
  instrument: Partial<Instrument>
}

interface Emits {
  (e: 'update:instrument', value: Partial<Instrument>): void
  (e: 'submit'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const localInstrument = ref<Partial<Instrument>>({ ...props.instrument })
const providerNames = Object.values(ProviderName)

watch(
  () => props.instrument,
  newVal => {
    localInstrument.value = { ...newVal }
  },
  { deep: true }
)

watch(
  localInstrument,
  newVal => {
    emit('update:instrument', newVal)
  },
  { deep: true }
)

const handleSubmit = () => {
  emit('submit')
}
</script>
