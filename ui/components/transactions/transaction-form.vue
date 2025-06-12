<template>
  <form @submit.prevent="handleSubmit">
    <FormInput
      v-model="formData.instrumentId"
      label="Instrument"
      type="select"
      :options="instrumentOptions"
      :error="errors.instrumentId"
      placeholder="Select Instrument"
      required
    />

    <FormInput
      v-model="formData.platform"
      label="Platform"
      type="select"
      :options="platformOptions"
      :error="errors.platform"
      placeholder="Select Platform"
      required
    />

    <FormInput
      v-model="formData.transactionType"
      label="Transaction Type"
      type="select"
      :options="transactionTypeOptions"
      :error="errors.transactionType"
      placeholder="Select Transaction Type"
      required
    />

    <FormInput
      v-model="formData.quantity"
      label="Quantity"
      type="number"
      :error="errors.quantity"
      placeholder="Enter quantity"
      step="0.00000001"
      min="0"
      required
    />

    <FormInput
      v-model="formData.price"
      label="Price"
      type="number"
      :error="errors.price"
      placeholder="Enter price"
      step="0.01"
      min="0"
      required
    />

    <FormInput
      v-model="formData.transactionDate"
      label="Transaction Date"
      type="date"
      :error="errors.transactionDate"
      required
    />

    <div v-if="totalValue" class="alert alert-info">
      <strong>Total Value:</strong>
      {{ formatCurrency(totalValue) }}
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useFormValidation, validators } from '../../composables/use-form-validation'
import FormInput from '../shared/form-input.vue'
import { platformOptions, transactionTypeOptions } from '../../constants/form-options'
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

const { formData, errors, validate } = useFormValidation<Partial<PortfolioTransaction>>(
  props.initialData,
  {
    instrumentId: validators.required('Instrument is required'),
    platform: validators.required('Platform is required'),
    transactionType: validators.required('Transaction type is required'),
    quantity: (value: any) => {
      if (!value) return 'Quantity is required'
      if (value < 0.00000001) return 'Quantity must be greater than 0'
      return true
    },
    price: (value: any) => {
      if (!value) return 'Price is required'
      if (value < 0.01) return 'Price must be greater than 0'
      return true
    },
    transactionDate: validators.required('Transaction date is required'),
  }
)

const instrumentOptions = computed(() =>
  props.instruments.map(instrument => ({
    value: instrument.id!,
    text: `${instrument.symbol} - ${instrument.name}`,
  }))
)

const totalValue = computed(() => {
  const quantity = formData.value.quantity || 0
  const price = formData.value.price || 0
  return quantity * price
})

watch(
  () => formData.value.instrumentId,
  newInstrumentId => {
    if (newInstrumentId) {
      const instrument = props.instruments.find(inst => inst.id === newInstrumentId)
      if (instrument && instrument.currentPrice && instrument.currentPrice > 0) {
        formData.value.price = instrument.currentPrice
      }
    }
  }
)

if (!formData.value.transactionDate) {
  formData.value.transactionDate = new Date().toISOString().split('T')[0]
}

const handleSubmit = async () => {
  if (await validate()) {
    emit('submit', formData.value)
  }
}

defineExpose({ handleSubmit })
</script>
