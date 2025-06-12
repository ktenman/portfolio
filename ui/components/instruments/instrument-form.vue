<template>
  <form @submit.prevent="handleSubmit">
    <FormInput
      v-model="formData.symbol"
      label="Symbol"
      :error="errors.symbol"
      placeholder="Enter instrument symbol"
      required
    />
    <FormInput v-model="formData.name" label="Name" placeholder="Enter instrument name" required />
    <FormInput
      v-model="formData.providerName"
      label="Data Provider"
      type="select"
      :options="providerOptions"
      placeholder="Select Data Provider"
      required
    />
    <FormInput
      v-model="formData.category"
      label="Category"
      type="select"
      :options="categoryOptions"
      placeholder="Select Instrument Category"
      required
    />
    <FormInput
      v-model="formData.baseCurrency"
      label="Currency"
      type="select"
      :options="currencyOptions"
      placeholder="Select Currency"
      required
    />
  </form>
</template>

<script setup lang="ts">
import { watch, computed } from 'vue'
import { Instrument } from '../../models/instrument'
import { ProviderName } from '../../constants/provider-name'
import { useFormValidation, validators } from '../../composables/use-form-validation'
import FormInput from '../shared/form-input.vue'

interface Props {
  initialData?: Partial<Instrument>
}

const props = withDefaults(defineProps<Props>(), {
  initialData: () => ({}),
})

const emit = defineEmits<{
  submit: [data: Partial<Instrument>]
}>()

const { formData, errors, validate } = useFormValidation<Partial<Instrument>>(props.initialData, {
  symbol: validators.required('Symbol is required'),
  name: validators.required('Name is required'),
  providerName: validators.required('Data provider is required'),
  category: validators.required('Category is required'),
  baseCurrency: validators.required('Currency is required'),
})

const providerOptions = computed(() =>
  Object.entries(ProviderName).map(([value, text]) => ({ value, text }))
)

const categoryOptions = [
  { value: 'STOCK', text: 'Stock' },
  { value: 'ETF', text: 'ETF' },
  { value: 'MUTUAL_FUND', text: 'Mutual Fund' },
  { value: 'BOND', text: 'Bond' },
  { value: 'CRYPTO', text: 'Cryptocurrency' },
]

const currencyOptions = [
  { value: 'USD', text: 'USD' },
  { value: 'EUR', text: 'EUR' },
  { value: 'GBP', text: 'GBP' },
]

watch(
  () => props.initialData,
  newData => {
    formData.value = { ...newData }
  },
  { deep: true }
)

const handleSubmit = () => {
  if (validate()) {
    emit('submit', formData.value)
  }
}

defineExpose({
  handleSubmit,
})
</script>
