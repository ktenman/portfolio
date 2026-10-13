<template>
  <div class="breakdown-panel card-shell">
    <div class="panel-header">
      <div class="breakdown-toolbar">
        <div class="breakdown-tabs" role="group" aria-label="Breakdown dimension">
          <button
            v-for="tab in TABS"
            :key="tab.key"
            class="breakdown-tab"
            :class="{ active: currentTab.key === tab.key }"
            :aria-pressed="currentTab.key === tab.key"
            type="button"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>
        <div class="breakdown-controls">
          <template v-if="benchmarkLabel">
            <span class="platform-separator" aria-hidden="true"></span>
            <label class="compare-switch compare-toggle">
              <input v-model="compare" type="checkbox" role="switch" class="compare-input" />
              <span class="compare-track" aria-hidden="true"></span>
              <span class="compare-label">
                <span class="compare-prefix">vs</span>
                {{ benchmarkLabel }}
              </span>
            </label>
          </template>
          <view-switch v-model="view" />
        </div>
      </div>
    </div>
    <div v-if="isHoldings" class="holdings-search">
      <search-input
        v-model="query"
        placeholder="Search by name or ticker..."
        label="Search holdings"
        :maxlength="100"
      />
      <span v-if="!holdingsError" class="holdings-count" role="status">{{ countLabel }}</span>
    </div>
    <p v-if="holdingsError" role="alert" class="holdings-message">{{ holdingsError }}</p>
    <p v-else-if="emptyHoldings" class="holdings-message" role="status">
      {{ query.trim() ? `No holdings match “${query.trim()}”` : 'No holdings in this allocation' }}
    </p>
    <template v-else>
      <breakdown-bars
        v-if="view === 'bars'"
        :rows="rows"
        :benchmark-label="benchmarkLabel"
        :holdings="isHoldings"
        :class="{ searching: isSearching }"
        :aria-busy="isSearching"
      />
      <breakdown-donut
        v-else
        :rows="rows"
        :format-value="isHoldings ? formatHoldingPercentage : undefined"
        :class="{ searching: isSearching }"
        :aria-busy="isSearching"
      />
      <div
        v-if="isHoldings && view === 'bars' && hasMore"
        :key="holdings?.length"
        ref="more"
        class="holdings-more"
      >
        <template v-if="moreFailed">
          <span role="alert">Couldn’t load more holdings</span>
          <app-button ghost size="sm" @click="loadMore">
            {{ loadingMore ? 'Retrying…' : 'Retry' }}
          </app-button>
        </template>
        <template v-else>Loading more…</template>
      </div>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { computed, defineAsyncComponent, toRef, useTemplateRef } from 'vue'
import { useIntersectionObserver, useLocalStorage } from '@vueuse/core'
import { STORAGE_KEYS } from '../../constants'
import AppButton from '../shared/app-button.vue'
import BreakdownBars from '../shared/breakdown-bars.vue'
import ViewSwitch from '../shared/view-switch.vue'
import SearchInput from '../shared/search-input.vue'
import { useHoldingsSearch } from '../../composables/use-holdings-search'
import { formatHoldingPercentage } from '../../utils/formatters'
import type { AllocationDto } from '../../models/generated/domain-models'
import {
  compareBreakdown,
  optionsForView,
  paint,
  unlessAbsent,
  COUNTRY_MIN_PERCENTAGE,
  INDUSTRY_MIN_PERCENTAGE,
  INDUSTRY_TOP_COUNT,
  SECTOR_MIN_PERCENTAGE,
  TOP_COUNT,
  type BreakdownView,
  type CompareOptions,
} from '../../services/diversification-chart-service'
import type { Breakdowns } from '../../composables/use-diversification-result'

const BreakdownDonut = defineAsyncComponent(() => import('../shared/breakdown-donut.vue'))

const props = defineProps<{
  allocations: AllocationDto[]
  breakdowns: Breakdowns
  benchmark: Breakdowns | null
  benchmarkLabel?: string
}>()

interface Tab {
  key: keyof Breakdowns
  label: string
  options: CompareOptions
}

const TABS: readonly Tab[] = [
  {
    key: 'sectors',
    label: 'Sectors',
    options: { topCount: TOP_COUNT, minPercentage: SECTOR_MIN_PERCENTAGE, withOther: true },
  },
  {
    key: 'industries',
    label: 'Industries',
    options: {
      topCount: INDUSTRY_TOP_COUNT,
      minPercentage: INDUSTRY_MIN_PERCENTAGE,
      withOther: true,
    },
  },
  {
    key: 'holdings',
    label: 'Holdings',
    options: { topCount: Infinity, minPercentage: 0, withOther: false },
  },
  {
    key: 'countries',
    label: 'Countries',
    options: { topCount: TOP_COUNT, minPercentage: COUNTRY_MIN_PERCENTAGE, withOther: true },
  },
]

const activeTab = useLocalStorage<keyof Breakdowns>(
  STORAGE_KEYS.DIVERSIFICATION_BREAKDOWN_TAB,
  'industries'
)

const compare = useLocalStorage(STORAGE_KEYS.BENCHMARK_COMPARE, true)

const view = useLocalStorage<BreakdownView>(STORAGE_KEYS.DIVERSIFICATION_BREAKDOWN_VIEW, 'bars')

const compared = computed(() => compare.value && props.benchmark !== null)

const currentTab = computed(() => TABS.find(t => t.key === activeTab.value) ?? TABS[1])

const isHoldings = computed(() => currentTab.value.key === 'holdings')
const { query, holdings, total, hasMore, moreFailed, loadingMore, loadMore, isSearching, error } =
  useHoldingsSearch(toRef(props, 'allocations'), isHoldings)
useIntersectionObserver(
  useTemplateRef<HTMLElement>('more'),
  entries => {
    if (entries.some(entry => entry.isIntersecting)) loadMore()
  },
  { rootMargin: '0px 0px 100% 0px' }
)
const holdingsError = computed(() => (isHoldings.value ? error.value : ''))
const emptyHoldings = computed(
  () => isHoldings.value && !isSearching.value && holdings.value?.length === 0
)
const countLabel = computed(() => {
  if (isSearching.value) return 'Searching…'
  const [one, many] = query.value.trim() ? ['match', 'matches'] : ['holding', 'holdings']
  return `${total.value.toLocaleString('en-US')} ${total.value === 1 ? one : many}`
})

const rows = computed(() => {
  const items = isHoldings.value
    ? (holdings.value ?? []).map(holding => ({
        label: holding.name,
        value: holding.percentage,
      }))
    : props.breakdowns[currentTab.value.key]
  const result = compareBreakdown(
    items,
    compared.value ? (props.benchmark?.[currentTab.value.key] ?? null) : null,
    optionsForView(currentTab.value.options, view.value)
  )
  return paint(isHoldings.value ? result.map(unlessAbsent) : result)
})
</script>

<style scoped>
.holdings-search {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.holdings-count,
.holdings-more {
  font-size: var(--text-label);
  color: var(--color-ink-muted);
}

.holdings-count {
  white-space: nowrap;
}

.holdings-message {
  margin: 1.5rem 0;
  color: var(--color-ink-soft);
}

.searching {
  opacity: 0.5;
}

.holdings-more {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding-top: 0.5rem;
  border-top: 1px solid var(--color-hairline);
}

.panel-header {
  display: flex;
  margin-bottom: 0.75rem;
}

.breakdown-toolbar,
.breakdown-tabs,
.breakdown-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.25rem;
}

.breakdown-controls {
  margin-left: auto;
}

.breakdown-controls .platform-separator {
  margin: 0 0.25rem;
}

.breakdown-tab {
  padding: 0.3125rem 0.75rem;
  border: 1px solid transparent;
  border-radius: var(--radius-container);
  background: transparent;
  font-size: 0.8125rem;
  color: var(--color-ink-soft);
  cursor: pointer;
  white-space: nowrap;
}

.breakdown-tab:hover {
  background: var(--color-surface-hover);
  color: var(--color-ink);
}

.breakdown-tab.active {
  border-color: var(--color-brass);
  background: var(--color-brass-wash);
  color: var(--color-brass-deep);
}

@media (max-width: 639px) {
  .holdings-search {
    flex-direction: column;
    align-items: stretch;
    gap: 0.375rem;
  }

  .breakdown-toolbar {
    width: 100%;
  }

  .breakdown-tabs,
  .breakdown-controls {
    gap: 0.375rem;
  }

  .breakdown-tab {
    padding: 0.3125rem 0;
    border: 0;
    border-bottom: 0.125rem solid transparent;
    border-radius: 0;
    font-size: var(--text-label);
  }

  .breakdown-tab.active,
  .breakdown-tab:hover {
    background: transparent;
  }

  .compare-switch {
    gap: 0.25rem;
    padding-inline: 0.125rem;
    font-size: var(--text-label);
  }
}

@media (min-width: 576px) and (max-width: 639px) {
  .compare-prefix {
    display: none;
  }
}

@media (max-width: 575px) {
  .breakdown-toolbar {
    row-gap: 0.75rem;
  }

  .breakdown-tabs {
    flex-basis: 100%;
    justify-content: space-between;
    column-gap: 0.75rem;
    border-bottom: 1px solid var(--color-hairline);
  }

  .breakdown-tab {
    margin-bottom: -1px;
  }

  .breakdown-controls {
    flex-basis: 100%;
  }

  .breakdown-controls .view-switch {
    margin-left: auto;
  }
}
</style>
