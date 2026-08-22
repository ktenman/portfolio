<template>
  <div>
    <div v-if="isLoading">
      <skeleton-loader
        type="table"
        :rows="5"
        :columns="columns.length + ($slots.actions ? 1 : 0)"
      />
    </div>

    <AlertMessage v-else-if="isError" variant="danger">
      {{ errorMessage || 'Failed to load data. Please try again.' }}
    </AlertMessage>

    <AlertMessage v-else-if="hasNoData" variant="info">
      {{ emptyMessage }}
    </AlertMessage>

    <template v-else>
      <!-- Mobile Card View -->
      <div class="mobile-cards-wrapper block md:hidden">
        <div
          v-for="(item, index) in items"
          :key="getItemKey(item, index)"
          class="mobile-card mb-2 overflow-hidden rounded-container border border-gray-200 bg-surface px-3 py-5 shadow-[0_1px_2px_0_rgb(0_0_0/0.03)] transition-all min-[389px]:px-2 min-[389px]:py-3 hover:border-gray-300 hover:shadow-lifted"
          :class="rowClass?.(item, index)"
        >
          <slot name="mobile-card" :item="item" :index="index" :columns="columns">
            <div class="mobile-card-body p-4">
              <div
                v-for="column in columns.filter(col => !col.hideOnMobile)"
                :key="column.key"
                class="mobile-card-item flex items-center justify-between gap-2 py-2 not-last:border-b not-last:border-b-gray-100 first:pt-0 last:pb-0 nth-2:mb-1"
              >
                <span
                  class="label min-w-20 flex-none text-2xs font-medium tracking-wider text-gray-600 uppercase"
                >
                  {{ column.label }}
                </span>
                <span
                  class="value ml-auto flex-1 text-right font-semibold wrap-anywhere min-[389px]:text-2xs"
                  :class="column.class"
                >
                  <slot
                    :name="`cell-${column.key}`"
                    :item="item"
                    :value="getCellValue(item, column)"
                  >
                    {{ formatCellValue(item, column) }}
                  </slot>
                </span>
              </div>
            </div>
            <div v-if="$slots.actions" class="mobile-card-actions">
              <slot name="actions" :item="item" :index="index"></slot>
            </div>
          </slot>
        </div>
        <!-- Mobile Footer -->
        <div v-if="$slots['mobile-footer']" class="mobile-footer">
          <slot name="mobile-footer"></slot>
        </div>
      </div>

      <!-- Desktop Table View -->
      <div class="desktop-table-wrapper hidden md:block table-responsive">
        <table class="table table-striped">
          <thead>
            <tr>
              <th
                v-for="column in columns"
                :key="column.key"
                :class="[column.class, { sortable: sortable && column.sortable !== false }]"
                @click="handleSort(column)"
              >
                <span class="th-content">
                  {{ column.label }}
                  <span
                    v-if="sortable && column.sortable !== false"
                    class="sort-indicator"
                    :class="{
                      active: sortState?.key === column.key,
                      asc: sortState?.key === column.key && sortState?.direction === 'asc',
                      desc: sortState?.key === column.key && sortState?.direction === 'desc',
                    }"
                  >
                    <i class="sort-arrow-up">▲</i>
                    <i class="sort-arrow-down">▼</i>
                  </span>
                </span>
              </th>
              <th v-if="$slots.actions" class="hidden! text-right! md:table-cell!">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(item, index) in items"
              :key="getItemKey(item, index)"
              :class="rowClass?.(item, index)"
            >
              <td
                v-for="column in columns"
                :key="column.key"
                :class="column.class"
                :data-label="column.label"
              >
                <slot :name="`cell-${column.key}`" :item="item" :value="getCellValue(item, column)">
                  {{ formatCellValue(item, column) }}
                </slot>
              </td>
              <td
                v-if="$slots.actions"
                class="hidden! text-right! md:table-cell!"
                data-label="Actions"
              >
                <slot name="actions" :item="item" :index="index"></slot>
              </td>
            </tr>
          </tbody>
          <tfoot v-if="$slots.footer">
            <slot name="footer"></slot>
          </tfoot>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts" generic="T extends Record<string, any>">
import { computed } from 'vue'
import SkeletonLoader from './skeleton-loader.vue'
import AlertMessage from './alert-message.vue'
import type { SortState } from '../../composables/use-sortable-table'

export interface ColumnDefinition {
  key: string
  label: string
  formatter?: (value: any, item?: any) => string
  class?: string
  hideOnMobile?: boolean
  sortable?: boolean
  sortKey?: string
}

interface Props {
  items: T[]
  columns: ColumnDefinition[]
  isLoading?: boolean
  isError?: boolean
  errorMessage?: string
  emptyMessage?: string
  keyField?: string
  rowClass?: (item: T, index: number) => string | Record<string, boolean>
  sortable?: boolean
  sortState?: SortState
  onSort?: (key: string, sortKey?: string) => void
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
  isError: false,
  emptyMessage: 'No data available',
  keyField: 'id',
  sortable: false,
})

const hasNoData = computed(() => !props.items || props.items.length === 0)

const getItemKey = (item: T, index: number): string | number => {
  if (props.keyField && props.keyField in item) {
    return item[props.keyField]
  }
  return index
}

const getCellValue = (item: T, column: ColumnDefinition): any => {
  const keys = column.key.split('.')
  let value: any = item

  for (const key of keys) {
    value = value?.[key]
  }

  return value
}

const formatCellValue = (item: T, column: ColumnDefinition): string => {
  const value = getCellValue(item, column)

  if (column.formatter) {
    return column.formatter(value, item)
  }

  if (value === null || value === undefined) {
    return '-'
  }

  return String(value)
}

const handleSort = (column: ColumnDefinition) => {
  if (props.sortable && column.sortable !== false && props.onSort) {
    props.onSort(column.key, column.sortKey)
  }
}
</script>

<style scoped>
.desktop-table-wrapper {
  overflow-x: auto;
  border: 1px solid var(--color-hairline-strong);
  border-radius: var(--radius-container);
  box-shadow: var(--shadow-card);
}

.table {
  margin-bottom: 0;
  font-size: 0.9rem;
}

@media (max-width: 666px) {
  .table {
    display: block;
    width: 100%;
    overflow: hidden;
  }

  .table thead {
    display: none;
  }

  .table tbody,
  .table tr,
  .table td {
    display: block;
    width: 100%;
  }

  .table tr {
    margin-bottom: 1rem;
    border-bottom: 1px solid var(--color-hairline-strong);
  }

  .table td {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0.5rem;
    font-size: 1rem;
    text-align: left;
  }

  .table td[data-label]::before {
    content: attr(data-label);
    flex-shrink: 0;
    width: 50%;
    margin-right: 0.5rem;
    font-weight: bold;
    color: var(--color-gray-600);
  }

  .table td.text-right\! {
    justify-content: flex-end;
  }
}

.sortable {
  cursor: pointer;
  user-select: none;
}

.sortable:hover {
  background-color: rgb(0 0 0 / 0.02);
}

.th-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.table th.text-right\! .th-content {
  justify-content: flex-end;
}

.sort-indicator {
  display: inline-flex;
  flex-direction: column;
  font-size: 0.65rem;
  line-height: 0.5;
  opacity: 0.3;
  transition: opacity var(--transition-base);
}

.sort-indicator.active {
  opacity: 1;
}

.sort-arrow-up,
.sort-arrow-down {
  display: block;
  height: 0.5rem;
}

.sort-indicator.asc .sort-arrow-up,
.sort-indicator.desc .sort-arrow-down {
  color: var(--color-signal-indigo);
}

.sort-indicator.asc .sort-arrow-down,
.sort-indicator.desc .sort-arrow-up {
  opacity: 0.3;
}

@media (orientation: landscape) and (max-width: 767px) {
  .mobile-cards-wrapper {
    display: none !important;
  }

  .desktop-table-wrapper {
    display: block !important;
  }
}
</style>
