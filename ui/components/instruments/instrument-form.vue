<template>
  <form id="instrumentForm" @submit.prevent="handleSubmit">
    <FormInput
      v-model="formData.symbol"
      label="Symbol"
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
import { watch, computed, reactive } from 'vue'
import { Instrument } from '../../models/instrument'
import { ProviderName } from '../../models/provider-name'
import { currencyOptions, categoryOptions } from '../../config'
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

const formData = reactive<Partial<Instrument>>({ ...props.initialData })

const providerOptions = computed(() =>
  Object.entries(ProviderName).map(([value, text]) => ({ value, text }))
)

watch(
  () => props.initialData,
  newData => {
    Object.assign(formData, newData)
  },
  { deep: true }
)

const handleSubmit = () => {
  emit('submit', formData)
}
</script>
