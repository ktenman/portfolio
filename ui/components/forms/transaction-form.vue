<template>
  <form @submit.prevent="handleSubmit">
    <div class="mb-3">
      <label for="instrumentId" class="form-label">Instrument</label>
      <select
        v-model="localTransaction.instrumentId"
        id="instrumentId"
        class="form-select"
        required
      >
        <option value="" disabled selected>Select Instrument</option>
        <option v-for="instrument in instruments" :key="instrument.id" :value="instrument.id">
          {{ instrument.symbol }} - {{ instrument.name }}
        </option>
      </select>
    </div>
    <div class="mb-3">
      <label for="platform" class="form-label">Platform</label>
      <select v-model="localTransaction.platform" id="platform" class="form-select" required>
        <option value="" disabled selected>Select Platform</option>
        <option v-for="platform in Object.values(Platform)" :key="platform" :value="platform">
          {{ platform }}
        </option>
      </select>
    </div>
    <div class="mb-3">
      <label for="transactionType" class="form-label">Transaction Type</label>
      <select
        v-model="localTransaction.transactionType"
        id="transactionType"
        class="form-select"
        required
      >
        <option value="" disabled selected>Select Transaction Type</option>
        <option value="BUY">Buy</option>
        <option value="SELL">Sell</option>
      </select>
    </div>
    <div class="mb-3">
      <label for="quantity" class="form-label">Quantity</label>
      <input
        v-model="localTransaction.quantity"
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
        v-model="localTransaction.price"
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
        v-model="localTransaction.transactionDate"
        type="date"
        class="form-control"
        id="transactionDate"
        required
      />
    </div>
  </form>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'
import { Platform } from '../../models/platform'

interface Props {
  transaction: Partial<PortfolioTransaction>
  instruments: Instrument[]
}

interface Emits {
  (e: 'update:transaction', value: Partial<PortfolioTransaction>): void
  (e: 'submit'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const localTransaction = ref<Partial<PortfolioTransaction>>({ ...props.transaction })

watch(
  () => props.transaction,
  newVal => {
    localTransaction.value = { ...newVal }
  },
  { deep: true }
)

watch(
  localTransaction,
  newVal => {
    emit('update:transaction', newVal)
  },
  { deep: true }
)

const handleSubmit = () => {
  emit('submit')
}
</script>

