<template>
  <form @submit.prevent="handleSubmit">
    <div class="mb-3">
      <label for="symbol" class="form-label">Symbol</label>
      <input
        v-model="formData.symbol"
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
        v-model="formData.name"
        type="text"
        class="form-control"
        id="name"
        placeholder="Enter instrument name"
        required
      />
    </div>

    <div class="mb-3">
      <label for="providerName" class="form-label">Data Provider</label>
      <select v-model="formData.providerName" id="providerName" class="form-select" required>
        <option value="" disabled>Select Data Provider</option>
        <option v-for="(label, value) in ProviderName" :key="value" :value="value">
          {{ label }}
        </option>
      </select>
    </div>

    <div class="mb-3">
      <label for="category" class="form-label">Category</label>
      <select v-model="formData.category" id="category" class="form-select" required>
        <option value="" disabled>Select Instrument Category</option>
        <option value="STOCK">Stock</option>
        <option value="ETF">ETF</option>
        <option value="MUTUAL_FUND">Mutual Fund</option>
        <option value="BOND">Bond</option>
        <option value="CRYPTO">Cryptocurrency</option>
      </select>
    </div>

    <div class="mb-3">
      <label for="currency" class="form-label">Currency</label>
      <select v-model="formData.baseCurrency" id="currency" class="form-select" required>
        <option value="" disabled>Select Currency</option>
        <option value="USD">USD</option>
        <option value="EUR">EUR</option>
        <option value="GBP">GBP</option>
        <!-- Add more currency options as needed -->
      </select>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Instrument } from '../../models/instrument'
import { ProviderName } from '../../constants/provider-name'

interface Props {
  initialData?: Partial<Instrument>
}

const props = withDefaults(defineProps<Props>(), {
  initialData: () => ({}),
})

const emit = defineEmits<{
  submit: [data: Partial<Instrument>]
}>()

const formData = ref<Partial<Instrument>>({
  ...props.initialData,
})

watch(
  () => props.initialData,
  newData => {
    formData.value = { ...newData }
  },
  { deep: true }
)

const handleSubmit = () => {
  if (isValidInstrument(formData.value)) {
    emit('submit', formData.value)
  }
}

const isValidInstrument = (instrument: Partial<Instrument>): boolean => {
  return (
    !!instrument.symbol &&
    !!instrument.name &&
    !!instrument.providerName &&
    !!instrument.category &&
    !!instrument.baseCurrency
  )
}

defineExpose({
  handleSubmit,
})
</script>
