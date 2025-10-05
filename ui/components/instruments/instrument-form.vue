<template>
  <form id="instrumentForm" novalidate @submit.prevent="handleSubmit">
    <FormInput
      :model-value="formData.symbol"
      label="Symbol"
      placeholder="Enter instrument symbol"
      :error="getFieldError('symbol')"
      :disabled="!!initialData?.id"
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
      :disabled="!!initialData?.id"
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
      :disabled="!!initialData?.id"
      @update:model-value="updateField('category', $event)"
      @blur="touchField('category')"
    />
    <FormInput
      :model-value="formData.baseCurrency || 'EUR'"
      label="Currency"
      type="text"
      disabled
      placeholder="EUR"
    />
    <FormInput
      :model-value="formData.currentPrice"
      label="Current Price"
      type="number"
      step="0.01"
      placeholder="Enter current price (optional)"
      :error="getFieldError('currentPrice')"
      @update:model-value="updateField('currentPrice', $event)"
      @blur="touchField('currentPrice')"
    />
  </form>
</template>

<script setup lang="ts">
import { watch, onMounted } from 'vue'
import { Instrument } from '../../models/instrument'
import FormInput from '../shared/form-input.vue'
import { useFormValidation } from '../../composables/use-form-validation'
import { useEnumValues } from '../../composables/use-enum-values'
import { instrumentSchema } from '../../schemas/instrument-schema'

interface Props {
  initialData?: Partial<Instrument>
}

const props = withDefaults(defineProps<Props>(), {
  initialData: () => ({}),
})

const emit = defineEmits<{
  submit: [data: Partial<Instrument>]
}>()

const { formData, validateForm, updateField, touchField, getFieldError, resetForm } =
  useFormValidation(instrumentSchema, {
    ...props.initialData,
    baseCurrency: props.initialData.baseCurrency || 'EUR',
  })

watch(
  () => props.initialData,
  newData => {
    if (newData) {
      resetForm({
        ...newData,
        baseCurrency: newData.baseCurrency || 'EUR',
      })
    }
  },
  { deep: true }
)

const { providerOptions, categoryOptions, loadAll } = useEnumValues()

onMounted(() => {
  loadAll()
})

const handleSubmit = () => {
  if (validateForm()) {
    emit('submit', {
      ...formData,
      baseCurrency: 'EUR',
    } as Partial<Instrument>)
  } else {
    Object.keys(instrumentSchema.shape).forEach(field => touchField(field))
  }
}
</script>
