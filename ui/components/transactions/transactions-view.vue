<template>
  <crud-layout
    add-button-id="addNewTransaction"
    add-button-text="New Transaction"
    title="Transactions"
    :show-add-button="false"
    @add="openAddModal"
  >
    <template #content>
      <div v-if="transactions?.length" class="row mb-3">
        <div class="col-md-4">
          <div class="card">
            <div class="card-body">
              <h6 class="card-subtitle mb-2 text-muted">Total Realized Profit</h6>
              <h4 :class="realizedProfitSum >= 0 ? 'text-success' : 'text-danger'">
                {{ formatCurrency(realizedProfitSum) }}
              </h4>
            </div>
          </div>
        </div>
        <div class="col-md-4">
          <div class="card">
            <div class="card-body">
              <h6 class="card-subtitle mb-2 text-muted">Total Unrealized Profit</h6>
              <h4 :class="unrealizedProfitSum >= 0 ? 'text-success' : 'text-danger'">
                {{ formatCurrency(unrealizedProfitSum) }}
              </h4>
            </div>
          </div>
        </div>
        <div class="col-md-4">
          <div class="card">
            <div class="card-body">
              <h6 class="card-subtitle mb-2 text-muted">Total Profit</h6>
              <h4 class="fw-bold" :class="totalProfitSum >= 0 ? 'text-success' : 'text-danger'">
                {{ formatCurrency(totalProfitSum) }}
              </h4>
            </div>
          </div>
        </div>
      </div>
      <transaction-table
        :is-loading="isLoading"
        :transactions="transactions || []"
        :instruments="instruments || []"
        @delete="handleDelete"
        @edit="openEditModal"
      />
    </template>

    <template #modals>
      <transaction-modal
        :instruments="instruments || []"
        :transaction="selectedItem || {}"
        @save="handleSave"
      />
    </template>
  </crud-layout>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { useToast } from '../../composables/use-toast'
import { useBootstrapModal } from '../../composables/use-bootstrap-modal'
import { useConfirm } from '../../composables/use-confirm'
import CrudLayout from '../shared/crud-layout.vue'
import TransactionTable from './transaction-table.vue'
import TransactionModal from './transaction-modal.vue'
import { instrumentsService } from '../../services/instruments-service'
import { transactionsService } from '../../services/transactions-service'
import { PortfolioTransaction } from '../../models/portfolio-transaction'
import { formatCurrency } from '../../utils/formatters'

const selectedItem = ref<PortfolioTransaction | null>(null)
const { show: showModal, hide: hideModal } = useBootstrapModal('transactionModal')
const { confirm } = useConfirm()
const queryClient = useQueryClient()
const toast = useToast()

const { data: transactions, isLoading } = useQuery({
  queryKey: ['transactions'],
  queryFn: transactionsService.getAll,
})

const { data: instruments } = useQuery({
  queryKey: ['instruments'],
  queryFn: () => instrumentsService.getAll(),
})

const realizedProfitSum = computed(() => {
  if (!transactions.value) return 0
  return transactions.value.reduce((sum, t) => sum + (t.realizedProfit || 0), 0)
})

const unrealizedProfitSum = computed(() => {
  if (!transactions.value) return 0
  return transactions.value.reduce((sum, t) => sum + (t.unrealizedProfit || 0), 0)
})

const totalProfitSum = computed(() => {
  return realizedProfitSum.value + unrealizedProfitSum.value
})

const saveMutation = useMutation({
  mutationFn: (data: Partial<PortfolioTransaction>) => {
    if (selectedItem.value?.id) {
      return transactionsService.update(selectedItem.value.id, data)
    }
    return transactionsService.create(data)
  },
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['transactions'] })
    toast.success(`Transaction ${selectedItem.value?.id ? 'updated' : 'created'} successfully`)
    hideModal()
    selectedItem.value = null
  },
  onError: (error: Error) => {
    toast.error(`Failed to save transaction: ${error.message}`)
  },
})

const deleteMutation = useMutation({
  mutationFn: transactionsService.delete,
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['transactions'] })
    toast.success('Transaction deleted successfully')
  },
  onError: (error: Error) => {
    toast.error(`Failed to delete transaction: ${error.message}`)
  },
})

const openAddModal = () => {
  selectedItem.value = {
    transactionDate: new Date().toISOString().split('T')[0],
  } as PortfolioTransaction
  showModal()
}

const openEditModal = (transaction: PortfolioTransaction) => {
  selectedItem.value = { ...transaction }
  showModal()
}

const handleSave = (transaction: Partial<PortfolioTransaction>) => {
  saveMutation.mutate(transaction)
}

const handleDelete = async (id: number | string) => {
  const shouldDelete = await confirm({
    title: 'Delete Transaction',
    message: 'Are you sure you want to delete this transaction?',
    confirmText: 'Delete',
    cancelText: 'Cancel',
    confirmClass: 'btn-danger',
  })

  if (shouldDelete) {
    deleteMutation.mutate(id)
  }
}
</script>
