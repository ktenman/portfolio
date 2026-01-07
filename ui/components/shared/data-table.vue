<template>
  <div>
    <div v-if="isLoading">
      <skeleton-loader
        type="table"
        :rows="5"
        :columns="columns.length + ($slots.actions ? 1 : 0)"
      />
    </div>

    <div v-else-if="isError" class="alert alert-danger" role="alert">
      {{ errorMessage || 'Failed to load data. Please try again.' }}
    </div>

    <div v-else-if="hasNoData" class="alert alert-info" role="alert">
      {{ emptyMessage }}
    </div>

    <template v-else>
      <!-- Mobile Card View -->
      <div class="mobile-cards-wrapper d-block d-md-none">
        <div
          v-for="(item, index) in items"
          :key="getItemKey(item, index)"
          class="mobile-card"
          :class="rowClass?.(item, index)"
        >
          <slot name="mobile-card" :item="item" :index="index" :columns="columns">
            <div class="mobile-card-body">
              <div
                v-for="column in columns.filter(col => !col.class?.includes('d-none'))"
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
      <div class="desktop-table-wrapper d-none d-md-block table-responsive">
        <table class="table table-striped table-hover">
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
              <th v-if="$slots.actions" class="text-end d-none d-md-table-cell">Actions</th>
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
                class="text-end d-none d-md-table-cell"
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
import type { SortState } from '../../composables/use-sortable-table'

export interface ColumnDefinition {
  key: string
  label: string
  formatter?: (value: any, item?: any) => string
  class?: string
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

<style scoped lang="scss">
.mobile-cards-wrapper {
  .mobile-card {
    background: var(--bs-white);
    border: 1px solid var(--bs-gray-200);
    border-radius: 0.5rem;
    margin-bottom: 0.5rem;
    overflow: hidden;
    transition: all var(--transition-fast);

    &:hover {
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    }

    .mobile-card-body {
      padding: 1rem;

      .mobile-card-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 0.5rem 0;
        gap: 0.5rem;

        &:not(:last-child) {
          border-bottom: 1px solid var(--bs-gray-100);
        }

        &:first-child {
          padding-top: 0;
        }

        &:last-child {
          padding-bottom: 0;
        }

        .label {
          color: var(--bs-gray-600);
          font-size: 0.8125rem;
          font-weight: 500;
          flex: 0 0 auto;
          min-width: 80px;
        }

        .value {
          font-weight: 600;
          text-align: right;
          flex: 1;
          word-break: break-word;
          margin-left: auto;

          &.text-success {
            color: var(--modern-success);
          }

          &.text-danger {
            color: var(--modern-danger);
          }

          &.text-end {
            text-align: right;
          }

          // Special styling for instrument info
          .instrument-info {
            > span:first-child {
              font-weight: 600;
              margin-bottom: 0.125rem;
              font-size: 0.95rem;
              color: var(--bs-gray-900) !important;
            }

            small {
              opacity: 1;
              font-weight: 500;
              font-size: 0.8125rem;
              color: var(--bs-gray-600) !important;
            }
          }
        }
      }
    }

    .mobile-card-actions {
      margin-top: 0.25rem;
      padding: 0.25rem 1rem 1rem;
      border-top: 1px solid var(--bs-gray-200);
      display: flex;
      gap: 0.75rem;
      justify-content: flex-end;
      flex-wrap: wrap;

      .btn {
        flex: 0 0 auto;
      }
    }
  }
}
.desktop-table-wrapper {
  border: 1px solid #dee2e6;
  border-radius: 0.5rem;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.table {
  font-size: 0.9rem;
  margin-bottom: 0;
}

.table th,
.table td {
  vertical-align: middle;
}

@media (max-width: 666px) {
  .table {
    font-size: 2.8vw;
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
    border-bottom: 1px solid #dee2e6;
  }

  .table td {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0.5rem;
    font-size: 1rem;
    text-align: left;
  }

  .table td[data-label]:before {
    content: attr(data-label);
    font-weight: bold;
    color: #6c757d;
    margin-right: 0.5rem;
    width: 50%;
    flex-shrink: 0;
  }

  .table td.text-end {
    justify-content: flex-end;
  }
}

.sortable {
  cursor: pointer;
  user-select: none;

  &:hover {
    background-color: rgba(0, 0, 0, 0.02);
  }
}

.th-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.sort-indicator {
  display: inline-flex;
  flex-direction: column;
  font-size: 0.65rem;
  line-height: 0.5;
  opacity: 0.3;
  transition: opacity 0.2s;

  &.active {
    opacity: 1;
  }

  .sort-arrow-up,
  .sort-arrow-down {
    display: block;
    height: 0.5rem;
  }

  &.asc .sort-arrow-up {
    color: var(--bs-primary);
  }

  &.asc .sort-arrow-down {
    opacity: 0.3;
  }

  &.desc .sort-arrow-down {
    color: var(--bs-primary);
  }

  &.desc .sort-arrow-up {
    opacity: 0.3;
  }
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
