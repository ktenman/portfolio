<template>
  <form id="instrumentForm" novalidate @submit.prevent="handleSubmit">
    <FormInput
      :model-value="formData.symbol"
      label="Symbol"
      placeholder="Enter instrument symbol"
      :error="getFieldError('symbol')"
      @update:model-value="updateField('symbol', $event)"
      @blur="touchField('symbol')"
    />
    <FormInput
      :model-value="formData.name"
      label="Name"
      placeholder="Enter instrument name"
      :error="getFieldError('name')"
      @update:model-value="updateField('name', $event)"
      @blur="touchField('name')"
    />
    <FormInput
      :model-value="formData.providerName"
      label="Data Provider"
      type="select"
      :options="providerOptions"
      placeholder="Select Data Provider"
      :error="getFieldError('providerName')"
      @update:model-value="updateField('providerName', $event)"
      @blur="touchField('providerName')"
    />
    <FormInput
      :model-value="formData.category"
      label="Category"
      type="select"
      :options="categoryOptions"
      placeholder="Select Instrument Category"
      :error="getFieldError('category')"
      @update:model-value="updateField('category', $event)"
      @blur="touchField('category')"
    />
    <FormInput
      :model-value="formData.baseCurrency"
      label="Currency"
      type="select"
      :options="currencyOptions"
      placeholder="Select Currency"
      :error="getFieldError('baseCurrency')"
      @update:model-value="updateField('baseCurrency', $event)"
      @blur="touchField('baseCurrency')"
    />
  </form>
</template>

<script setup lang="ts">
import { watch, onMounted } from 'vue'
import { z } from 'zod'
import { Instrument } from '../../models/instrument'
import { ProviderName } from '../../models/provider-name'
import FormInput from '../shared/form-input.vue'
import { useFormValidation } from '../../composables/use-form-validation'
import { useEnumValues } from '../../composables/use-enum-values'

interface Props {
  initialData?: Partial<Instrument>
}

const props = withDefaults(defineProps<Props>(), {
  initialData: () => ({}),
})

const emit = defineEmits<{
  submit: [data: Partial<Instrument>]
}>()

const instrumentSchema = z.object({
  symbol: z.string().min(1, 'Symbol is required').max(10, 'Symbol must be 10 characters or less'),
  name: z.string().min(1, 'Name is required').max(100, 'Name must be 100 characters or less'),
  providerName: z.nativeEnum(ProviderName, {
    errorMap: () => ({ message: 'Please select a data provider' }),
  }),
  category: z.string().min(1, 'Category is required'),
  baseCurrency: z.string().min(1, 'Currency is required'),
})

const { formData, validateForm, updateField, touchField, getFieldError, resetForm } =
  useFormValidation(instrumentSchema, props.initialData)

watch(
  () => props.initialData,
  newData => {
    if (newData) {
      resetForm(newData)
    }
  },
  { deep: true }
)

const { providerOptions, categoryOptions, currencyOptions, loadAll } = useEnumValues()

onMounted(() => {
  loadAll()
})

const handleSubmit = () => {
  if (validateForm()) {
    emit('submit', formData)
  } else {
    Object.keys(instrumentSchema.shape).forEach(field => touchField(field))
  }
}
</script>
