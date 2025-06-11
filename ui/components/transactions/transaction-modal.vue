<template>
  <div
    class="modal fade"
    :id="modalId"
    tabindex="-1"
    :aria-labelledby="`${modalId}Label`"
    aria-hidden="true"
  >
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" :id="`${modalId}Label`">
            {{ isEditing ? 'Edit Transaction' : 'Add New Transaction' }}
          </h5>
          <button
            type="button"
            class="btn-close"
            data-bs-dismiss="modal"
            aria-label="Close"
          ></button>
        </div>
        <div class="modal-body">
          <transaction-form
            ref="formRef"
            :initial-data="transaction"
            :instruments="instruments"
            @submit="handleSave"
          />
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">
            Cancel
          </button>
          <button type="button" class="btn btn-primary" @click="triggerSubmit">
            {{ isEditing ? 'Update' : 'Save' }} Transaction
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import TransactionForm from './transaction-form.vue'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { Instrument } from '../../models/instrument'

interface Props {
  modalId?: string
  transaction?: Partial<PortfolioTransaction>
  instruments: Instrument[]
}

const props = withDefaults(defineProps<Props>(), {
  modalId: 'transactionModal',
  transaction: () => ({
    transactionDate: new Date().toISOString().split('T')[0]
  })
})

const emit = defineEmits<{
  save: [data: Partial<PortfolioTransaction>]
}>()

const formRef = ref<InstanceType<typeof TransactionForm>>()

const isEditing = computed(() => !!props.transaction?.id)

const handleSave = (data: Partial<PortfolioTransaction>) => {
  emit('save', data)
}

const triggerSubmit = () => {
  formRef.value?.handleSubmit()
}
</script>