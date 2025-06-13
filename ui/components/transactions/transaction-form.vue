<template>
  <form id="transactionForm" @submit.prevent="handleSubmit">
    <FormInput
      v-model="formData.instrumentId"
      label="Instrument"
      type="select"
      :options="instrumentOptions"
      placeholder="Select Instrument"
      required
    />

    <FormInput
      v-model="formData.platform"
      label="Platform"
      type="select"
      :options="platformOptions"
      placeholder="Select Platform"
      required
    />

    <FormInput
      v-model="formData.transactionType"
      label="Transaction Type"
      type="select"
      :options="transactionTypeOptions"
      placeholder="Select Transaction Type"
      required
    />

    <FormInput
      v-model="formData.quantity"
      label="Quantity"
      type="number"
      placeholder="Enter quantity"
      step="0.00000001"
      min="0.00000001"
      required
    />

    <FormInput
      v-model="formData.price"
      label="Price"
      type="number"
      placeholder="Enter price"
      step="0.01"
      min="0.01"
      required
    />

    <FormInput v-model="formData.transactionDate" label="Transaction Date" type="date" required />

    <div v-if="totalValue" class="alert alert-info">
      <strong>Total Value:</strong>
      {{ formatCurrency(totalValue) }}
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed, watch, reactive } from 'vue'
import FormInput from '../shared/form-input.vue'
import { platformOptions, transactionTypeOptions } from '../../config'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'
import { formatCurrency } from '../../utils/formatters'

interface Props {
  initialData?: Partial<PortfolioTransaction>
  instruments: Instrument[]
}

const props = withDefaults(defineProps<Props>(), {
  initialData: () => ({
    transactionDate: new Date().toISOString().split('T')[0],
  }),
})

const emit = defineEmits<{
  submit: [data: Partial<PortfolioTransaction>]
}>()

const formData = reactive<Partial<PortfolioTransaction>>({ ...props.initialData })

const instrumentOptions = computed(() =>
  props.instruments.map(instrument => ({
    value: instrument.id!,
    text: `${instrument.symbol} - ${instrument.name}`,
  }))
)

const totalValue = computed(() => {
  const quantity = formData.quantity || 0
  const price = formData.price || 0
  return quantity * price
})

watch(
  () => props.initialData,
  newData => {
    Object.assign(formData, newData)
  },
  { deep: true }
)

watch(
  () => formData.instrumentId,
  newInstrumentId => {
    if (newInstrumentId) {
      const instrument = props.instruments.find(inst => inst.id === newInstrumentId)
      if (instrument && instrument.currentPrice && instrument.currentPrice > 0) {
        formData.price = instrument.currentPrice
      }
    }
  }
)

if (!formData.transactionDate) {
  formData.transactionDate = new Date().toISOString().split('T')[0]
}

const handleSubmit = () => {
  emit('submit', formData)
}
</script>
