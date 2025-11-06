<template>
  <div class="container mt-3">
    <div class="mb-4">
      <h2 class="mb-0">Transactions</h2>
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
    </div>

    <div v-if="transactions?.length" class="stats-container mb-4">
      <div class="stat-card">
        <div class="stat-label">Total Realized Profit</div>
        <div class="stat-value" :class="realizedProfitSum >= 0 ? 'text-success' : 'text-danger'">
          {{ formatCurrency(realizedProfitSum) }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Total Unrealized Profit</div>
        <div class="stat-value" :class="unrealizedProfitSum >= 0 ? 'text-success' : 'text-danger'">
          {{ formatCurrency(unrealizedProfitSum) }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Total Profit</div>
        <div class="stat-value" :class="totalProfitSum >= 0 ? 'text-success' : 'text-danger'">
          {{ formatCurrency(totalProfitSum) }}
        </div>
      </div>
    </div>

    <transaction-table :is-loading="isLoading" :transactions="transactions || []" />
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useLocalStorage } from '@vueuse/core'
import TransactionTable from './transaction-table.vue'
import { transactionsService } from '../../services/transactions-service'
import { formatCurrency } from '../../utils/formatters'
import { formatPlatformName } from '../../utils/platform-utils'

const selectedPlatforms = useLocalStorage<string[]>('portfolio_selected_transaction_platforms', [])

const { data: allTransactions, isLoading } = useQuery({
  queryKey: ['transactions'],
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

const transactions = computed(() => {
  if (!allTransactions.value) return []
  if (selectedPlatforms.value.length === 0) return allTransactions.value
  return allTransactions.value.filter(
    t => t.platform && selectedPlatforms.value.includes(t.platform)
  )
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
</script>

<style scoped>
.stats-container {
  display: flex;
  flex-direction: row;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.stat-card {
  background: white;
  border: 1px solid #e0e0e0;
  padding: 1rem 1.5rem;
  border-radius: 0.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  flex: 1;
  min-width: 200px;
}

.stat-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: #6c757d;
  font-weight: 500;
  margin-bottom: 0.25rem;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  line-height: 1.2;
  color: #1a1a1a;
}

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
  .stats-container {
    flex-direction: column;
  }

  .stat-card {
    padding: 0.75rem 1rem;
    min-width: unset;
  }

  .stat-value {
    font-size: 1.25rem;
  }

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

@media (max-width: 926px) and (orientation: landscape) {
  .stats-container {
    flex-direction: row;
    justify-content: center;
  }

  .stat-card {
    padding: 1.25rem 2rem;
    min-width: 200px;
  }

  .stat-label {
    font-size: 0.8rem;
  }

  .stat-value {
    font-size: 1.5rem;
  }
}

@media (max-width: 768px) {
  :deep(.mobile-card-actions) {
    display: none !important;
  }
}
</style>
