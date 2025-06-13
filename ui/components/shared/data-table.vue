<template>
  <div>
    <div v-if="isLoading" class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading...</span>
    </div>

    <div v-else-if="isError" class="alert alert-danger" role="alert">
      {{ errorMessage || 'Failed to load data. Please try again.' }}
    </div>

    <div v-else-if="hasNoData" class="alert alert-info" role="alert">
      {{ emptyMessage }}
    </div>

    <div v-else class="table-responsive">
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
  </div>
</template>

<script setup lang="ts" generic="T extends Record<string, any>">
import { computed } from 'vue'
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

<style scoped>
.table {
  font-size: 0.9rem;
}

.table th,
.table td {
  vertical-align: middle;
}

@media (max-width: 767px) {
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

  .btn-sm {
    padding: 0.25rem 0.5rem;
  }
}
</style>
