<template>
  <div class="tw:mx-auto tw:mt-4 tw:w-full tw:max-w-app tw:px-3">
    <div class="tw:mb-6">
      <h2 class="tw:mb-0">Transactions</h2>
      <div class="filters-container tw:mt-2">
        <div class="date-filters">
          <div class="date-inputs-row">
            <div class="date-input-group">
              <label for="fromDate" class="date-label">From</label>
              <input
                id="fromDate"
                v-model="fromDate"
                type="date"
                class="form-control form-control-sm"
              />
            </div>
            <div class="date-input-group">
              <label for="untilDate" class="date-label">Until</label>
              <input
                id="untilDate"
                v-model="untilDate"
                type="date"
                class="form-control form-control-sm"
              />
            </div>
          </div>
          <div class="date-actions-row">
            <div class="dropdown" ref="quickDateDropdown">
              <button
                class="platform-btn dropdown-toggle"
                :class="{ active: selectedQuickDate }"
                type="button"
                data-testid="quickDatesToggle"
                :aria-expanded="isDropdownOpen"
                @click="isDropdownOpen = !isDropdownOpen"
              >
                {{ selectedQuickDate || 'Quick Dates' }}
              </button>
              <ul class="dropdown-menu" :class="{ show: isDropdownOpen }">
                <li v-for="option in QUICK_DATE_OPTIONS" :key="option.preset">
                  <a class="dropdown-item" @click="handleQuickDateSelect(option.preset)">
                    {{ option.label }}
                  </a>
                </li>
              </ul>
            </div>
            <button
              v-if="fromDate || untilDate"
              class="platform-btn"
              @click="clearDates"
              type="button"
            >
              Clear Dates
            </button>
          </div>
        </div>
        <platform-filter
          v-if="availablePlatforms.length > 0"
          :available="availablePlatforms"
          :selected="selectedPlatforms"
          @toggle="togglePlatform"
          @toggle-all="toggleAllPlatforms"
        />
      </div>
    </div>

    <div v-if="transactions?.length" class="stats-container tw:mb-6">
      <div class="stat-card">
        <div class="stat-label">Total Realized Profit</div>
        <div class="stat-value" :class="realizedProfitSum >= 0 ? 'tw:text-gain!' : 'tw:text-loss!'">
          {{ formatCurrency(realizedProfitSum) }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Total Unrealized Profit</div>
        <div
          class="stat-value"
          :class="unrealizedProfitSum >= 0 ? 'tw:text-gain!' : 'tw:text-loss!'"
        >
          {{ formatCurrency(unrealizedProfitSum) }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Total Profit</div>
        <div class="stat-value" :class="totalProfitSum >= 0 ? 'tw:text-gain!' : 'tw:text-loss!'">
          {{ formatCurrency(totalProfitSum) }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Total Invested</div>
        <div class="stat-value">
          {{ formatCurrency(totalInvested) }}
        </div>
      </div>
    </div>

    <transaction-table :is-loading="isLoading" :transactions="transactions || []" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { onClickOutside, onKeyStroke } from '@vueuse/core'
import TransactionTable from './transaction-table.vue'
import PlatformFilter from '../shared/platform-filter.vue'
import { transactionsService } from '../../services/transactions-service'
import { formatCurrency } from '../../utils/formatters'
import { STORAGE_KEYS } from '../../constants'
import {
  useQuickDates,
  QUICK_DATE_OPTIONS,
  type QuickDatePreset,
} from '../../composables/use-quick-dates'
import { useAuthState } from '../../composables/use-auth-state'
import { usePlatformFilter } from '../../composables/use-platform-filter'
const { isAuthenticated } = useAuthState()
const quickDateDropdown = ref<HTMLElement | null>(null)
const isDropdownOpen = ref(false)

const closeDropdown = () => {
  isDropdownOpen.value = false
}

onClickOutside(quickDateDropdown, closeDropdown)
onKeyStroke('Escape', closeDropdown)

const { fromDate, untilDate, selectedQuickDate, setQuickDate, clearDates } = useQuickDates({
  fromDateKey: STORAGE_KEYS.TRANSACTIONS_FROM_DATE,
  untilDateKey: STORAGE_KEYS.TRANSACTIONS_UNTIL_DATE,
  selectedQuickDateKey: STORAGE_KEYS.SELECTED_QUICK_DATE,
  onDateSet: closeDropdown,
})

const { data: allTransactionsResponse } = useQuery({
  queryKey: ['transactions'],
  queryFn: () => transactionsService.getAll(),
  enabled: isAuthenticated,
})

const availablePlatforms = computed(() => {
  if (!allTransactionsResponse.value) return []

  const platformSet = new Set<string>()
  allTransactionsResponse.value.transactions.forEach(transaction => {
    if (transaction.platform) {
      platformSet.add(transaction.platform)
    }
  })

  return Array.from(platformSet).sort()
})

const { selectedPlatforms, togglePlatform, toggleAllPlatforms } = usePlatformFilter(
  STORAGE_KEYS.SELECTED_TRANSACTION_PLATFORMS,
  availablePlatforms
)

const { data: transactionsResponse, isLoading } = useQuery({
  queryKey: ['transactions', selectedPlatforms, fromDate, untilDate],
  queryFn: () =>
    transactionsService.getAll(
      selectedPlatforms.value.length > 0 ? selectedPlatforms.value : undefined,
      fromDate.value || undefined,
      untilDate.value || undefined
    ),
  enabled: isAuthenticated,
})

const transactions = computed(() => transactionsResponse.value?.transactions)

const realizedProfitSum = computed(() => {
  return transactionsResponse.value?.summary.totalRealizedProfit || 0
})

const unrealizedProfitSum = computed(() => {
  return transactionsResponse.value?.summary.totalUnrealizedProfit || 0
})

const totalProfitSum = computed(() => {
  return transactionsResponse.value?.summary.totalProfit || 0
})

const totalInvested = computed(() => {
  return transactionsResponse.value?.summary.totalInvested || 0
})

const handleQuickDateSelect = (preset: QuickDatePreset) => {
  setQuickDate(preset)
}
</script>

<style scoped>
.filters-container {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.date-filters {
  display: grid;
  grid-template-columns: max-content max-content max-content max-content;
  gap: 0.375rem 0.75rem;
  align-items: end;
}

.date-inputs-row {
  display: contents;
}

.date-actions-row {
  display: contents;
}

.date-input-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.date-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: #4b5563;
  margin-bottom: 0;
}

.date-input-group {
  width: 110px;
}

.date-input-group .form-control {
  width: 100%;
}

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

.date-actions {
  display: flex;
  gap: 0.375rem;
  align-items: center;
}

.date-actions-row .platform-btn,
.date-actions-row .dropdown {
  width: 110px;
}

.date-actions-row .dropdown-toggle {
  width: 100%;
}

.dropdown-menu {
  top: 100%;
  left: 0;
  margin-top: 0.125rem;
}

.dropdown-item {
  cursor: pointer;
  font-size: 0.875rem;
}

.dropdown-item:active {
  background-color: #4b5563;
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

  .date-filters {
    grid-template-columns: max-content max-content;
    gap: 0.375rem 0.5rem;
  }

  .date-input-group {
    width: 110px;
  }

  .date-label {
    font-size: 0.75rem;
  }

  .date-input-group .form-control {
    width: 100%;
    font-size: 0.875rem;
    padding: 0.375rem 0.5rem;
  }

  :deep(.platform-btn) {
    font-size: 0.75rem;
    padding: 0.375rem 0.5rem;
  }

  .dropdown-toggle {
    font-size: 0.75rem;
    padding: 0.375rem 0.5rem;
  }

  .date-actions-row .platform-btn,
  .date-actions-row .dropdown {
    width: 110px;
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
