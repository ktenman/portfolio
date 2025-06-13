<template>
  <form id="transactionForm" novalidate @submit.prevent="handleSubmit">
    <FormInput
      :model-value="formData.instrumentId"
      label="Instrument"
      type="select"
      :options="instrumentOptions"
      placeholder="Select Instrument"
      :error="getFieldError('instrumentId')"
      @update:model-value="updateField('instrumentId', Number($event))"
      @blur="touchField('instrumentId')"
    />

    <FormInput
      :model-value="formData.platform"
      label="Platform"
      type="select"
      :options="platformOptions"
      placeholder="Select Platform"
      :error="getFieldError('platform')"
      @update:model-value="updateField('platform', $event)"
      @blur="touchField('platform')"
    />

    <FormInput
      :model-value="formData.transactionType"
      label="Transaction Type"
      type="select"
      :options="transactionTypeOptions"
      placeholder="Select Transaction Type"
      :error="getFieldError('transactionType')"
      @update:model-value="updateField('transactionType', $event)"
      @blur="touchField('transactionType')"
    />

    <FormInput
      :model-value="formData.quantity"
      label="Quantity"
      type="number"
      placeholder="Enter quantity"
      step="0.00000001"
      :error="getFieldError('quantity')"
      @update:model-value="updateField('quantity', Number($event))"
      @blur="touchField('quantity')"
    />

    <FormInput
      :model-value="formData.price"
      label="Price"
      type="number"
      placeholder="Enter price"
      step="0.001"
      :error="getFieldError('price')"
      @update:model-value="updateField('price', Number($event))"
      @blur="touchField('price')"
    />

    <FormInput
      :model-value="formData.transactionDate"
      label="Transaction Date"
      type="date"
      :error="getFieldError('transactionDate')"
      @update:model-value="updateField('transactionDate', $event)"
      @blur="touchField('transactionDate')"
    />

    <div v-if="totalValue" class="alert alert-info">
      <strong>Total Value:</strong>
      {{ formatCurrency(totalValue) }}
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed, watch, onMounted } from 'vue'
import FormInput from '../shared/form-input.vue'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'
import { formatCurrency } from '../../utils/formatters'
import { useFormValidation } from '../../composables/use-form-validation'
import { transactionSchema } from '../../schemas/transaction-schema'
import { useEnumValues } from '../../composables/use-enum-values'

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

const { formData, validateForm, updateField, touchField, getFieldError, resetForm } =
  useFormValidation(transactionSchema, {
    transactionDate: new Date().toISOString().split('T')[0],
    ...props.initialData,
  })

watch(
  () => props.initialData,
  newData => {
    if (newData) {
      resetForm({
        transactionDate: new Date().toISOString().split('T')[0],
        ...newData,
      })
    }
  },
  { deep: true }
)

const { platformOptions, transactionTypeOptions, loadAll } = useEnumValues()

onMounted(() => {
  loadAll()
})

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
  () => formData.instrumentId,
  newInstrumentId => {
    if (newInstrumentId) {
      const instrument = props.instruments.find(inst => inst.id === newInstrumentId)
      if (instrument && instrument.currentPrice && instrument.currentPrice > 0) {
        updateField('price', Number(instrument.currentPrice))
      } else {
        updateField('price', undefined)
      }
    }
  }
)

const handleSubmit = () => {
  if (validateForm()) {
    emit('submit', formData)
  } else {
    Object.keys(transactionSchema.shape).forEach(field => touchField(field))
  }
}
</script>
