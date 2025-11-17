<template>
  <div class="container mt-3">
    <div class="mb-4">
      <h2 class="mb-0">Transactions</h2>
      <div class="filters-container mt-2">
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
            <div class="dropdown">
              <button
                ref="quickDateDropdown"
                class="platform-btn dropdown-toggle"
                :class="{ active: selectedQuickDate }"
                type="button"
                data-bs-toggle="dropdown"
                aria-expanded="false"
              >
                {{ selectedQuickDate || 'Quick Dates' }}
              </button>
              <ul class="dropdown-menu">
                <li><a class="dropdown-item" @click="setToday">Today</a></li>
                <li><a class="dropdown-item" @click="setLast7Days">Last 7 Days</a></li>
                <li><a class="dropdown-item" @click="setThisWeek">This Week</a></li>
                <li><a class="dropdown-item" @click="setLastWeek">Last Week</a></li>
                <li><a class="dropdown-item" @click="setLast30Days">Last 30 Days</a></li>
                <li><a class="dropdown-item" @click="setThisMonth">This Month</a></li>
                <li><a class="dropdown-item" @click="setLastMonth">Last Month</a></li>
                <li><a class="dropdown-item" @click="setThisYear">This Year</a></li>
                <li><a class="dropdown-item" @click="setLastYear">Last Year</a></li>
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
        <div v-if="availablePlatforms.length > 0" class="platform-filter-container">
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
import { computed, ref, watch, nextTick } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useLocalStorage } from '@vueuse/core'
import { Dropdown } from 'bootstrap'
import TransactionTable from './transaction-table.vue'
import { transactionsService } from '../../services/transactions-service'
import { formatCurrency } from '../../utils/formatters'
import { formatPlatformName } from '../../utils/platform-utils'

const selectedPlatforms = useLocalStorage<string[]>('portfolio_selected_transaction_platforms', [])
const fromDate = useLocalStorage<string>('portfolio_transactions_from_date', '')
const untilDate = useLocalStorage<string>('portfolio_transactions_until_date', '')
const selectedQuickDate = useLocalStorage<string>('portfolio_selected_quick_date', '')
const quickDateDropdown = ref<HTMLElement | null>(null)

let manualDateChange = false

const closeDropdown = () => {
  if (quickDateDropdown.value) {
    const dropdownInstance = Dropdown.getInstance(quickDateDropdown.value)
    dropdownInstance?.hide()
  }
}

watch([fromDate, untilDate], () => {
  if (!manualDateChange) {
    selectedQuickDate.value = ''
  }
})

const { data: allTransactions } = useQuery({
  queryKey: ['transactions'],
  queryFn: () => transactionsService.getAll(),
})

const { data: transactions, isLoading } = useQuery({
  queryKey: ['transactions', selectedPlatforms, fromDate, untilDate],
  queryFn: () =>
    transactionsService.getAll(
      selectedPlatforms.value.length > 0 ? selectedPlatforms.value : undefined,
      fromDate.value || undefined,
      untilDate.value || undefined
    ),
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

const clearDates = () => {
  fromDate.value = ''
  untilDate.value = ''
  selectedQuickDate.value = ''
}

const formatDate = (date: Date): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const setToday = () => {
  manualDateChange = true
  const today = new Date()
  fromDate.value = formatDate(today)
  untilDate.value = formatDate(today)
  selectedQuickDate.value = 'Today'
  closeDropdown()
  nextTick(() => {
    manualDateChange = false
  })
}

const setLast7Days = () => {
  manualDateChange = true
  const today = new Date()
  const sevenDaysAgo = new Date(today)
  sevenDaysAgo.setDate(today.getDate() - 6)
  fromDate.value = formatDate(sevenDaysAgo)
  untilDate.value = formatDate(today)
  selectedQuickDate.value = 'Last 7 Days'
  closeDropdown()
  nextTick(() => {
    manualDateChange = false
  })
}

const setThisWeek = () => {
  manualDateChange = true
  const today = new Date()
  const dayOfWeek = today.getDay()
  const monday = new Date(today)
  monday.setDate(today.getDate() - (dayOfWeek === 0 ? 6 : dayOfWeek - 1))
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  fromDate.value = formatDate(monday)
  untilDate.value = formatDate(sunday)
  selectedQuickDate.value = 'This Week'
  closeDropdown()
  nextTick(() => {
    manualDateChange = false
  })
}

const setLastWeek = () => {
  manualDateChange = true
  const today = new Date()
  const dayOfWeek = today.getDay()
  const lastMonday = new Date(today)
  lastMonday.setDate(today.getDate() - (dayOfWeek === 0 ? 6 : dayOfWeek - 1) - 7)
  const lastSunday = new Date(lastMonday)
  lastSunday.setDate(lastMonday.getDate() + 6)
  fromDate.value = formatDate(lastMonday)
  untilDate.value = formatDate(lastSunday)
  selectedQuickDate.value = 'Last Week'
  closeDropdown()
  nextTick(() => {
    manualDateChange = false
  })
}

const setLast30Days = () => {
  manualDateChange = true
  const today = new Date()
  const thirtyDaysAgo = new Date(today)
  thirtyDaysAgo.setDate(today.getDate() - 29)
  fromDate.value = formatDate(thirtyDaysAgo)
  untilDate.value = formatDate(today)
  selectedQuickDate.value = 'Last 30 Days'
  closeDropdown()
  nextTick(() => {
    manualDateChange = false
  })
}

const setThisMonth = () => {
  manualDateChange = true
  const today = new Date()
  const firstDay = new Date(today.getFullYear(), today.getMonth(), 1)
  const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0)
  fromDate.value = formatDate(firstDay)
  untilDate.value = formatDate(lastDay)
  selectedQuickDate.value = 'This Month'
  closeDropdown()
  nextTick(() => {
    manualDateChange = false
  })
}

const setLastMonth = () => {
  manualDateChange = true
  const today = new Date()
  const firstDay = new Date(today.getFullYear(), today.getMonth() - 1, 1)
  const lastDay = new Date(today.getFullYear(), today.getMonth(), 0)
  fromDate.value = formatDate(firstDay)
  untilDate.value = formatDate(lastDay)
  selectedQuickDate.value = 'Last Month'
  closeDropdown()
  nextTick(() => {
    manualDateChange = false
  })
}

const setThisYear = () => {
  manualDateChange = true
  const today = new Date()
  const firstDay = new Date(today.getFullYear(), 0, 1)
  const lastDay = new Date(today.getFullYear(), 11, 31)
  fromDate.value = formatDate(firstDay)
  untilDate.value = formatDate(lastDay)
  selectedQuickDate.value = 'This Year'
  closeDropdown()
  nextTick(() => {
    manualDateChange = false
  })
}

const setLastYear = () => {
  manualDateChange = true
  const today = new Date()
  const firstDay = new Date(today.getFullYear() - 1, 0, 1)
  const lastDay = new Date(today.getFullYear() - 1, 11, 31)
  fromDate.value = formatDate(firstDay)
  untilDate.value = formatDate(lastDay)
  selectedQuickDate.value = 'Last Year'
  closeDropdown()
  nextTick(() => {
    manualDateChange = false
  })
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

  .platform-btn {
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
