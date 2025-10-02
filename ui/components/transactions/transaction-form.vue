<template>
  <form id="transactionForm" novalidate @submit.prevent="handleSubmit">
    <FormInput
      v-model="instrumentIdModel"
      label="Instrument"
      type="select"
      :options="instrumentOptions"
      placeholder="Select Instrument"
      :error="getFieldError('instrumentId')"
      id="instrumentId"
      @blur="touchField('instrumentId')"
    />

    <FormInput
      v-model="formData.platform"
      label="Platform"
      type="select"
      :options="platformOptions"
      placeholder="Select Platform"
      :error="getFieldError('platform')"
      id="platform"
      @blur="touchField('platform')"
    />

    <FormInput
      v-model="formData.transactionType"
      label="Transaction Type"
      type="select"
      :options="transactionTypeOptions"
      placeholder="Select Transaction Type"
      :error="getFieldError('transactionType')"
      id="transactionType"
      @blur="touchField('transactionType')"
    />

    <FormInput
      v-model="quantityModel"
      label="Quantity"
      type="number"
      placeholder="Enter quantity"
      step="0.00000001"
      :error="getFieldError('quantity')"
      id="quantity"
      @blur="touchField('quantity')"
    />

    <FormInput
      v-model="priceModel"
      label="Price"
      type="number"
      placeholder="Enter price"
      step="0.001"
      :error="getFieldError('price')"
      id="price"
      @blur="touchField('price')"
    />

    <FormInput
      v-model="commissionModel"
      label="Fee"
      type="number"
      placeholder="Enter fee (optional)"
      step="0.01"
      min="0"
      :error="getFieldError('commission')"
      id="commission"
      @blur="touchField('commission')"
    />

    <FormInput
      v-model="formData.currency"
      label="Currency"
      type="select"
      :options="currencyOptions"
      placeholder="Select Currency"
      :error="getFieldError('currency')"
      id="currency"
      @blur="touchField('currency')"
    />

    <FormInput
      v-model="formData.transactionDate"
      label="Transaction Date"
      type="date"
      :error="getFieldError('transactionDate')"
      id="transactionDate"
      @blur="touchField('transactionDate')"
    />

    <div v-if="totalValueWithCommission" class="alert alert-info">
      <strong>Total Value:</strong>
      {{ formatCurrencyWithSign(totalValueWithCommission, formData.currency) }}
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed, watch, onMounted } from 'vue'
import FormInput from '../shared/form-input.vue'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'
import { formatCurrencyWithSign } from '../../utils/formatters'
import { useFormValidation } from '../../composables/use-form-validation'
import { transactionSchema } from '../../schemas/transaction-schema'
import { useEnumValues } from '../../composables/use-enum-values'
import { SafeNumber } from '../../utils/safe-number'

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

const { formData, validateForm, touchField, getFieldError, resetForm } = useFormValidation(
  transactionSchema,
  {
    transactionDate: new Date().toISOString().split('T')[0],
    commission: 0,
    currency: 'EUR',
    ...props.initialData,
  }
)

watch(
  () => props.initialData,
  newData => {
    if (newData) {
      resetForm({
        transactionDate: new Date().toISOString().split('T')[0],
        commission: 0,
        currency: 'EUR',
        ...newData,
      })
    }
  },
  { deep: true }
)

const { platformOptions, transactionTypeOptions, currencyOptions, loadAll } = useEnumValues()

onMounted(() => {
  loadAll()
})

const instrumentOptions = computed(() =>
  props.instruments.map(instrument => ({
    value: instrument.id!,
    text: `${instrument.symbol} - ${instrument.name}`,
  }))
)

const instrumentIdModel = computed({
  get() {
    return formData.instrumentId
  },
  set(value: string | number) {
    formData.instrumentId = Number(value)
  },
})

const quantityModel = computed({
  get() {
    return formData.quantity === undefined ? '' : String(formData.quantity)
  },
  set(value: string) {
    formData.quantity = SafeNumber(value)
  },
})

const priceModel = computed({
  get() {
    return formData.price === undefined ? '' : String(formData.price)
  },
  set(value: string) {
    formData.price = SafeNumber(value)
  },
})

const commissionModel = computed({
  get() {
    return formData.commission === undefined ? '' : String(formData.commission)
  },
  set(value: string) {
    formData.commission = SafeNumber(value) || 0
  },
})

const totalValue = computed(() => {
  const quantity = formData.quantity || 0
  const price = formData.price || 0
  return quantity * price
})

const totalValueWithCommission = computed(() => {
  const baseValue = totalValue.value
  const commission = formData.commission || 0
  const transactionType = formData.transactionType

  if (transactionType === 'BUY') {
    return baseValue + commission
  } else if (transactionType === 'SELL') {
    return baseValue - commission
  }
  return baseValue
})

watch(
  () => formData.instrumentId,
  newInstrumentId => {
    if (newInstrumentId) {
      const instrument = props.instruments.find(inst => inst.id === newInstrumentId)
      if (instrument && instrument.currentPrice && instrument.currentPrice > 0) {
        priceModel.value = String(instrument.currentPrice)
      } else {
        priceModel.value = ''
      }
    }
  }
)

const handleSubmit = () => {
  if (validateForm()) {
    emit('submit', formData as Partial<PortfolioTransaction>)
  } else {
    Object.keys(transactionSchema.shape).forEach(field => touchField(field))
  }
}
</script>
