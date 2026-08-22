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
          class="mobile-card"
          :class="rowClass?.(item, index)"
        >
          <slot name="mobile-card" :item="item" :index="index" :columns="columns">
            <div class="mobile-card-body">
              <div
                v-for="column in columns.filter(col => !col.hideOnMobile)"
                :key="column.key"
                class="mobile-card-item"
              >
                <span class="label">{{ column.label }}</span>
                <span class="value" :class="column.class">
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
.mobile-cards-wrapper .mobile-card {
  margin-bottom: 0.5rem;
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-container);
  transition: all var(--transition-fast);
}

.mobile-cards-wrapper .mobile-card:hover {
  box-shadow: var(--shadow-lifted);
}

.mobile-cards-wrapper .mobile-card .mobile-card-body {
  padding: 1rem;
}

.mobile-cards-wrapper .mobile-card .mobile-card-body .mobile-card-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.5rem 0;
}

.mobile-cards-wrapper .mobile-card .mobile-card-body .mobile-card-item:not(:last-child) {
  border-bottom: 1px solid var(--color-gray-100);
}

.mobile-cards-wrapper .mobile-card .mobile-card-body .mobile-card-item:first-child {
  padding-top: 0;
}

.mobile-cards-wrapper .mobile-card .mobile-card-body .mobile-card-item:last-child {
  padding-bottom: 0;
}

.mobile-cards-wrapper .mobile-card .mobile-card-body .mobile-card-item .label {
  flex: 0 0 auto;
  min-width: 80px;
  font-size: var(--text-2xs);
  font-weight: 500;
  color: var(--color-gray-600);
}

.mobile-cards-wrapper .mobile-card .mobile-card-body .mobile-card-item .value {
  flex: 1;
  margin-left: auto;
  font-weight: 600;
  text-align: right;
  word-break: break-word;
}

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
