<template>
  <crud-layout
    add-button-id="addNewTransaction"
    add-button-text="New Transaction"
    title="Transactions"
    :show-add-button="false"
    @add="openAddModal"
  >
    <template #subtitle>
      <div v-if="availablePlatforms.length > 0" class="platform-filter-container mt-2">
        <div class="platform-buttons">
          <button
            v-for="platform in availablePlatforms"
            :key="platform"
            class="platform-btn"
            :class="{ active: isPlatformSelected(platform) }"
            @click="togglePlatform(platform)"
            type="button"
          >
            {{ formatPlatformName(platform) }}
          </button>
          <span class="platform-separator"></span>
          <button class="platform-btn" @click="toggleAllPlatforms" type="button">
            {{
              selectedPlatforms.length === availablePlatforms.length ? 'Clear All' : 'Select All'
            }}
          </button>
        </div>
      </div>
    </template>

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
import { ref, computed, watch } from 'vue'
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { useToast } from '../../composables/use-toast'
import { useLocalStorage } from '@vueuse/core'
import { useBootstrapModal } from '../../composables/use-bootstrap-modal'
import { useConfirm } from '../../composables/use-confirm'
import CrudLayout from '../shared/crud-layout.vue'
import TransactionTable from './transaction-table.vue'
import TransactionModal from './transaction-modal.vue'
import { instrumentsService } from '../../services/instruments-service'
import { transactionsService } from '../../services/transactions-service'
import { TransactionResponseDto } from '../../models/generated/domain-models'
import { formatCurrency } from '../../utils/formatters'
import { formatPlatformName } from '../../utils/platform-utils'

const selectedItem = ref<TransactionResponseDto | null>(null)
const selectedPlatforms = useLocalStorage<string[]>('portfolio_selected_transaction_platforms', [])
const { show: showModal, hide: hideModal } = useBootstrapModal('transactionModal')
const { confirm } = useConfirm()
const queryClient = useQueryClient()
const toast = useToast()

const { data: allTransactions } = useQuery({
  queryKey: ['transactions-all'],
  queryFn: () => transactionsService.getAll(),
})

const availablePlatforms = computed(() => {
  if (!allTransactions.value) return []

  const platformSet = new Set<string>()
  allTransactions.value.forEach(transaction => {
    if (transaction.platform) {
      platformSet.add(transaction.platform)
    }
  })

  return Array.from(platformSet).sort()
})

watch(
  availablePlatforms,
  newPlatforms => {
    if (newPlatforms.length > 0 && selectedPlatforms.value.length === 0) {
      selectedPlatforms.value = [...newPlatforms]
    } else if (newPlatforms.length > 0) {
      const validPlatforms = selectedPlatforms.value.filter(p => newPlatforms.includes(p))
      if (validPlatforms.length === 0) {
        selectedPlatforms.value = [...newPlatforms]
      } else if (validPlatforms.length !== selectedPlatforms.value.length) {
        selectedPlatforms.value = validPlatforms
      }
    }
  },
  { immediate: true }
)

const { data: transactions, isLoading } = useQuery({
  queryKey: computed(() => ['transactions', selectedPlatforms.value]),
  queryFn: () => {
    if (
      selectedPlatforms.value.length === 0 ||
      selectedPlatforms.value.length === availablePlatforms.value.length
    ) {
      return transactionsService.getAll()
    }
    return transactionsService.getAll(selectedPlatforms.value)
  },
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

const isPlatformSelected = (platform: string): boolean => {
  return selectedPlatforms.value.includes(platform)
}

const togglePlatform = (platform: string) => {
  const index = selectedPlatforms.value.indexOf(platform)
  if (index > -1) {
    selectedPlatforms.value = selectedPlatforms.value.filter(p => p !== platform)
  } else {
    selectedPlatforms.value = [...selectedPlatforms.value, platform]
  }
}

const toggleAllPlatforms = () => {
  if (selectedPlatforms.value.length === availablePlatforms.value.length) {
    selectedPlatforms.value = []
  } else {
    selectedPlatforms.value = [...availablePlatforms.value]
  }
}

const saveMutation = useMutation({
  mutationFn: (data: Partial<TransactionResponseDto>) => {
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
  } as TransactionResponseDto
  showModal()
}

const openEditModal = (transaction: TransactionResponseDto) => {
  selectedItem.value = { ...transaction }
  showModal()
}

const handleSave = (transaction: Partial<TransactionResponseDto>) => {
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

<style scoped>
.platform-filter-container {
  display: flex;
  align-items: center;
  padding: 0;
  background: transparent;
}

.platform-buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.375rem;
}

.platform-separator {
  width: 1px;
  height: 1.25rem;
  background-color: #d1d5db;
  display: inline-block;
}

.platform-btn {
  padding: 0.3125rem 0.625rem;
  border: 1px solid #e2e8f0;
  background: white;
  color: #6b7280;
  border-radius: 0.375rem;
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.12s ease;
  white-space: nowrap;
}

.platform-btn:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
  color: #4b5563;
}

.platform-btn:active {
  background: #f1f5f9;
  transform: scale(0.98);
}

.platform-btn.active {
  background: #4b5563;
  color: white;
  border-color: #4b5563;
  font-weight: 500;
}

.platform-btn.active:hover {
  background: #374151;
  border-color: #374151;
  color: white;
}

@media (max-width: 768px) {
  .platform-filter-container {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.375rem;
  }

  .platform-buttons {
    width: 100%;
  }

  .platform-separator {
    display: none;
  }
}

@media (min-width: 769px) {
  .platform-filter-container {
    align-items: center;
  }
}

@media (max-width: 768px) {
  :deep(.mobile-card-actions) {
    display: none !important;
  }
}
</style>
