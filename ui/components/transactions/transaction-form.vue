<template>
  <form @submit.prevent="handleSubmit">
    <div class="mb-3">
      <label for="instrumentId" class="form-label">Instrument</label>
      <select
        v-model.number="formData.instrumentId"
        id="instrumentId"
        class="form-select"
        required
      >
        <option value="" disabled>Select Instrument</option>
        <option
          v-for="instrument in instruments"
          :key="instrument.id"
          :value="instrument.id"
        >
          {{ instrument.symbol }} - {{ instrument.name }}
        </option>
      </select>
    </div>
    
    <div class="mb-3">
      <label for="platform" class="form-label">Platform</label>
      <select
        v-model="formData.platform"
        id="platform"
        class="form-select"
        required
      >
        <option value="" disabled>Select Platform</option>
        <option
          v-for="platform in Object.values(Platform)"
          :key="platform"
          :value="platform"
        >
          {{ platform }}
        </option>
      </select>
    </div>
    
    <div class="mb-3">
      <label for="transactionType" class="form-label">Transaction Type</label>
      <select
        v-model="formData.transactionType"
        id="transactionType"
        class="form-select"
        required
      >
        <option value="" disabled>Select Transaction Type</option>
        <option value="BUY">Buy</option>
        <option value="SELL">Sell</option>
      </select>
    </div>
    
    <div class="mb-3">
      <label for="quantity" class="form-label">Quantity</label>
      <input
        v-model.number="formData.quantity"
        type="number"
        step="0.00000001"
        class="form-control"
        id="quantity"
        placeholder="Enter quantity"
        required
      />
    </div>
    
    <div class="mb-3">
      <label for="price" class="form-label">Price</label>
      <input
        v-model.number="formData.price"
        type="number"
        step="0.01"
        class="form-control"
        id="price"
        placeholder="Enter price"
        required
      />
    </div>
    
    <div class="mb-3">
      <label for="transactionDate" class="form-label">Transaction Date</label>
      <input
        v-model="formData.transactionDate"
        type="date"
        class="form-control"
        id="transactionDate"
        required
      />
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'
import { Platform } from '../../models/platform'

interface Props {
  initialData?: Partial<PortfolioTransaction>
  instruments: Instrument[]
}

const props = withDefaults(defineProps<Props>(), {
  initialData: () => ({
    transactionDate: new Date().toISOString().split('T')[0]
  })
})

const emit = defineEmits<{
  submit: [data: Partial<PortfolioTransaction>]
}>()

const formData = ref<Partial<PortfolioTransaction>>({
  ...props.initialData
})

watch(() => props.initialData, (newData) => {
  formData.value = { ...newData }
}, { deep: true })

const handleSubmit = () => {
  if (isValidTransaction(formData.value)) {
    emit('submit', formData.value)
  }
}

const isValidTransaction = (
  transaction: Partial<PortfolioTransaction>
): boolean => {
  return (
    typeof transaction.instrumentId === 'number' &&
    (transaction.transactionType === 'BUY' || transaction.transactionType === 'SELL') &&
    typeof transaction.quantity === 'number' &&
    typeof transaction.price === 'number' &&
    typeof transaction.transactionDate === 'string' &&
    !!transaction.platform
  )
}

defineExpose({
  handleSubmit
})
</script>