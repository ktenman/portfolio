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
      placeholder="Select InstrumentDto Category"
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
import { InstrumentDto } from '../../models/generated/domain-models'
import FormInput from '../shared/form-input.vue'
import { useFormValidation } from '../../composables/use-form-validation'
import { useEnumValues } from '../../composables/use-enum-values'
import { instrumentSchema } from '../../schemas/instrument-schema'

interface Props {
  initialData?: Partial<InstrumentDto>
}

const props = withDefaults(defineProps<Props>(), {
  initialData: () => ({}),
})

const emit = defineEmits<{
  submit: [data: Partial<InstrumentDto>]
}>()

const { formData, validateForm, updateField, touchField, getFieldError, resetForm } =
  useFormValidation(instrumentSchema, {
    ...props.initialData,
    baseCurrency: props.initialData.baseCurrency || 'EUR',
    currentPrice: props.initialData.currentPrice ?? undefined,
    id: props.initialData.id ?? undefined,
  } as Record<string, unknown>)

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
    } as Partial<InstrumentDto>)
  } else {
    Object.keys(instrumentSchema.shape).forEach(field => touchField(field))
  }
}
</script>
