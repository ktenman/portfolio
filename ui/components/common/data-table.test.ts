import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { h } from 'vue'
import DataTable from './data-table.vue'
import type { TableColumn } from './data-table.vue'

describe('DataTable', () => {
  const basicColumns: TableColumn[] = [
    { key: 'id', label: 'ID' },
    { key: 'name', label: 'Name' },
    { key: 'age', label: 'Age' },
  ]

  const basicData = [
    { id: 1, name: 'John', age: 30 },
    { id: 2, name: 'Jane', age: 25 },
    { id: 3, name: 'Bob', age: 35 },
  ]

  it('renders table with correct structure', () => {
    const wrapper = mount(DataTable, {
      props: {
        columns: basicColumns,
        data: basicData,
      },
    })

    expect(wrapper.find('table').exists()).toBe(true)
    expect(wrapper.find('thead').exists()).toBe(true)
    expect(wrapper.find('tbody').exists()).toBe(true)
  })

  it('renders column headers correctly', () => {
    const wrapper = mount(DataTable, {
      props: {
        columns: basicColumns,
        data: basicData,
      },
    })

    const headers = wrapper.findAll('thead th')
    expect(headers).toHaveLength(3)
    expect(headers[0].text()).toBe('ID')
    expect(headers[1].text()).toBe('Name')
    expect(headers[2].text()).toBe('Age')
  })

  it('renders data rows correctly', () => {
    const wrapper = mount(DataTable, {
      props: {
        columns: basicColumns,
        data: basicData,
      },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(3)

    const firstRowCells = rows[0].findAll('td')
    expect(firstRowCells[0].text()).toBe('1')
    expect(firstRowCells[1].text()).toBe('John')
    expect(firstRowCells[2].text()).toBe('30')
  })

  it('applies header classes when provided', () => {
    const columnsWithClasses: TableColumn[] = [
      { key: 'id', label: 'ID', headerClass: 'text-center' },
      { key: 'name', label: 'Name', headerClass: 'text-start' },
    ]

    const wrapper = mount(DataTable, {
      props: {
        columns: columnsWithClasses,
        data: [{ id: 1, name: 'Test' }],
      },
    })

    const headers = wrapper.findAll('thead th')
    expect(headers[0].classes()).toContain('text-center')
    expect(headers[1].classes()).toContain('text-start')
  })

  it('applies cell classes when provided as string', () => {
    const columnsWithClasses: TableColumn[] = [{ key: 'id', label: 'ID', cellClass: 'text-center' }]

    const wrapper = mount(DataTable, {
      props: {
        columns: columnsWithClasses,
        data: [{ id: 1 }],
      },
    })

    const cell = wrapper.find('tbody td')
    expect(cell.classes()).toContain('text-center')
  })

  it('applies cell classes when provided as function', () => {
    const columnsWithClasses: TableColumn[] = [
      {
        key: 'status',
        label: 'Status',
        cellClass: (row: any) => (row.status === 'active' ? 'text-success' : 'text-danger'),
      },
    ]

    const wrapper = mount(DataTable, {
      props: {
        columns: columnsWithClasses,
        data: [{ status: 'active' }, { status: 'inactive' }],
      },
    })

    const cells = wrapper.findAll('tbody td')
    expect(cells[0].classes()).toContain('text-success')
    expect(cells[1].classes()).toContain('text-danger')
  })

  it('uses formatter function when provided', () => {
    const columnsWithFormatter: TableColumn[] = [
      {
        key: 'price',
        label: 'Price',
        formatter: (value: number) => `$${value.toFixed(2)}`,
      },
    ]

    const wrapper = mount(DataTable, {
      props: {
        columns: columnsWithFormatter,
        data: [{ price: 99.9 }, { price: 150 }],
      },
    })

    const cells = wrapper.findAll('tbody td')
    expect(cells[0].text()).toBe('$99.90')
    expect(cells[1].text()).toBe('$150.00')
  })

  it('uses keyField for row keys when provided', () => {
    const wrapper = mount(DataTable, {
      props: {
        columns: basicColumns,
        data: basicData,
        keyField: 'id',
      },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(3)
  })

  it('renders slot content when provided', () => {
    const wrapper = mount(DataTable, {
      props: {
        columns: basicColumns,
        data: basicData,
      },
      slots: {
        'cell-name': ({ row }) => h('strong', row.name),
      },
    })

    const nameCells = wrapper.findAll('tbody tr td:nth-child(2)')
    expect(nameCells[0].find('strong').exists()).toBe(true)
    expect(nameCells[0].find('strong').text()).toBe('John')
    expect(nameCells[1].find('strong').exists()).toBe(true)
    expect(nameCells[1].find('strong').text()).toBe('Jane')
  })

  it('adds data-label attribute to cells', () => {
    const wrapper = mount(DataTable, {
      props: {
        columns: basicColumns,
        data: [basicData[0]],
      },
    })

    const cells = wrapper.findAll('tbody td')
    expect(cells[0].attributes('data-label')).toBe('ID')
    expect(cells[1].attributes('data-label')).toBe('Name')
    expect(cells[2].attributes('data-label')).toBe('Age')
  })

  it('handles empty data array', () => {
    const wrapper = mount(DataTable, {
      props: {
        columns: basicColumns,
        data: [],
      },
    })

    expect(wrapper.find('tbody').exists()).toBe(true)
    expect(wrapper.findAll('tbody tr')).toHaveLength(0)
  })
})
