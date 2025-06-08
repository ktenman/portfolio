<template>
  <div class="table-responsive">
    <table class="table table-striped table-hover">
      <thead>
        <tr>
          <th v-for="column in columns" :key="column.key" :class="column.headerClass">
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in data" :key="getRowKey(row, index)">
          <td
            v-for="column in columns"
            :key="column.key"
            :class="getCellClass(column, row)"
            :data-label="column.label"
          >
            <slot :name="`cell-${column.key}`" :row="row" :value="getCellValue(row, column.key)">
              {{ getCellValue(row, column.key) }}
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script lang="ts" setup>
export interface TableColumn {
  key: string
  label: string
  headerClass?: string
  cellClass?: string | ((row: any) => string)
  formatter?: (value: any, row: any) => string
}

interface Props {
  columns: TableColumn[]
  data: any[]
  keyField?: string
}

const props = defineProps<Props>()

const getRowKey = (row: any, index: number): string | number => {
  if (props.keyField && row[props.keyField] !== undefined) {
    return row[props.keyField]
  }
  return index
}

const getCellValue = (row: any, key: string): any => {
  const column = props.columns.find(col => col.key === key)
  const value = row[key]

  if (column?.formatter) {
    return column.formatter(value, row)
  }

  return value
}

const getCellClass = (column: TableColumn, row: any): string => {
  if (typeof column.cellClass === 'function') {
    return column.cellClass(row)
  }
  return column.cellClass || ''
}
</script>

<style scoped>
/* Component-specific styles only - common styles are in common.css */
</style>
