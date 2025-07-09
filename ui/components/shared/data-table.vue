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
      <div class="d-block d-md-none mobile-cards">
        <div
          v-for="(item, index) in items"
          :key="getItemKey(item, index)"
          class="mobile-card"
          :class="rowClass?.(item, index)"
        >
          <div class="mobile-card-body">
            <div v-for="column in columns" :key="column.key" class="mobile-card-item">
              <span class="label">{{ column.label }}</span>
              <span class="value" :class="column.class">
                <slot :name="`cell-${column.key}`" :item="item" :value="getCellValue(item, column)">
                  {{ formatCellValue(item, column) }}
                </slot>
              </span>
            </div>
          </div>
          <div v-if="$slots.actions" class="mobile-card-actions">
            <slot name="actions" :item="item" :index="index"></slot>
          </div>
        </div>
      </div>

      <!-- Desktop Table View -->
      <div class="d-none d-md-block table-responsive">
        <table class="table table-striped table-hover">
          <thead>
            <tr>
              <th v-for="column in columns" :key="column.key" :class="column.class">
                {{ column.label }}
              </th>
              <th v-if="$slots.actions" class="text-end">Actions</th>
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
              <td v-if="$slots.actions" class="text-end" data-label="Actions">
                <slot name="actions" :item="item" :index="index"></slot>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts" generic="T extends Record<string, any>">
import { computed } from 'vue'
import SkeletonLoader from './skeleton-loader.vue'
export interface ColumnDefinition {
  key: string
  label: string
  formatter?: (value: any, item?: any) => string
  class?: string
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
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
  isError: false,
  emptyMessage: 'No data available',
  keyField: 'id',
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
</script>

<style scoped lang="scss">
.mobile-cards {
  .mobile-card {
    background: var(--bs-white);
    border: 1px solid var(--bs-gray-200);
    border-radius: var(--radius-lg);
    padding: 1rem 0.75rem;
    margin-bottom: 0.75rem;
    box-shadow: var(--shadow-sm);
    transition: all var(--transition-fast);

    @media (min-width: 389px) and (max-width: 767px) {
      padding: 0.75rem 0.5rem;
      margin-bottom: 0.5rem;
    }

    &:hover {
      box-shadow: var(--shadow-md);
    }

    .mobile-card-body {
      .mobile-card-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 0.75rem 0;
        gap: 0.5rem;

        &:not(:last-child) {
          border-bottom: 1px solid var(--bs-gray-100);
        }

        .label {
          color: var(--bs-gray-600);
          font-size: 0.875rem;
          font-weight: 500;
          flex: 0 0 auto;
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
      padding-top: 0.25rem;
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
.table {
  font-size: 0.9rem;
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

  .table .hide-on-mobile {
    display: none !important;
  }
}

@media (max-width: 794px) {
  .table .hide-on-mobile {
    display: none !important;
  }
}
</style>
