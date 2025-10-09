import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DataTable from './data-table.vue'
import type { ColumnDefinition } from './data-table.vue'

describe('DataTable', () => {
  const mockItems = [
    { id: 1, name: 'Item 1', price: 100, category: { name: 'Electronics' } },
    { id: 2, name: 'Item 2', price: 200, category: { name: 'Books' } },
    { id: 3, name: 'Item 3', price: 300, category: { name: 'Clothing' } },
  ] as any
  const mockColumns: ColumnDefinition[] = [
    { key: 'id', label: 'ID' },
    { key: 'name', label: 'Name' },
    { key: 'price', label: 'Price', formatter: (value: number) => `$${value}` },
    { key: 'category.name', label: 'Category' },
  ] as any
  describe('rendering states', () => {
    it('should render loading state', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: [],
          columns: mockColumns,
          isLoading: true,
        },
      })

      expect(wrapper.findComponent({ name: 'SkeletonLoader' }).exists()).toBe(true)
      expect(wrapper.find('.table').exists()).toBe(false)
    })

    it('should render error state', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: [],
          columns: mockColumns,
          isError: true,
          errorMessage: 'Custom error message',
        },
      })

      expect(wrapper.find('.alert-danger').exists()).toBe(true)
      expect(wrapper.find('.alert-danger').text()).toBe('Custom error message')
      expect(wrapper.find('.table').exists()).toBe(false)
    })

    it('should render default error message when no errorMessage provided', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: [],
          columns: mockColumns,
          isError: true,
        },
      })

      expect(wrapper.find('.alert-danger').text()).toBe('Failed to load data. Please try again.')
    })

    it('should render empty state', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: [],
          columns: mockColumns,
        },
      })

      expect(wrapper.find('.alert-info').exists()).toBe(true)
      expect(wrapper.find('.alert-info').text()).toBe('No data available')
      expect(wrapper.find('.table').exists()).toBe(false)
    })

    it('should render custom empty message', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: [],
          columns: mockColumns,
          emptyMessage: 'No records found',
        },
      })

      expect(wrapper.find('.alert-info').text()).toBe('No records found')
    })
  })

  describe('table rendering', () => {
    it('should render table with data', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: mockItems,
          columns: mockColumns,
        },
      })

      expect(wrapper.find('.table').exists()).toBe(true)
      expect(wrapper.findAll('thead th')).toHaveLength(4)
      expect(wrapper.findAll('tbody tr')).toHaveLength(3)
    })

    it('should render column headers', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: mockItems,
          columns: mockColumns,
        },
      })

      const headers = wrapper.findAll('thead th')
      expect(headers[0].text()).toBe('ID')
      expect(headers[1].text()).toBe('Name')
      expect(headers[2].text()).toBe('Price')
      expect(headers[3].text()).toBe('Category')
    })

    it('should render cell values', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: mockItems,
          columns: mockColumns,
        },
      })

      const firstRow = wrapper.findAll('tbody tr')[0].findAll('td')
      expect(firstRow[0].text()).toBe('1')
      expect(firstRow[1].text()).toBe('Item 1')
      expect(firstRow[2].text()).toBe('$100')
      expect(firstRow[3].text()).toBe('Electronics')
    })

    it('should handle nested properties', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: mockItems,
          columns: mockColumns,
        },
      })

      const cells = wrapper.findAll('tbody tr td:nth-child(4)')
      expect(cells[0].text()).toBe('Electronics')
      expect(cells[1].text()).toBe('Books')
      expect(cells[2].text()).toBe('Clothing')
    })

    it('should apply column classes', () => {
      const columnsWithClass: ColumnDefinition[] = [
        { key: 'id', label: 'ID', class: 'text-center' },
        { key: 'price', label: 'Price', class: 'text-end' },
      ] as any
      const wrapper = mount(DataTable, {
        props: {
          items: mockItems,
          columns: columnsWithClass,
        },
      })

      expect(wrapper.find('thead th:first-child').classes()).toContain('text-center')
      expect(wrapper.find('tbody td:first-child').classes()).toContain('text-center')
      expect(wrapper.find('thead th:nth-child(2)').classes()).toContain('text-end')
    })
  })

  describe('formatting and null handling', () => {
    it('should handle null and undefined values', () => {
      const itemsWithNulls = [
        { id: 1, name: null, price: undefined },
        { id: 2, name: 'Item 2', price: 200 },
      ] as any
      const wrapper = mount(DataTable, {
        props: {
          items: itemsWithNulls,
          columns: mockColumns.slice(0, 3),
        },
      })

      const firstRow = wrapper.findAll('tbody tr')[0].findAll('td')
      expect(firstRow[1].text()).toBe('-')
      expect(firstRow[2].text()).toBe('$undefined')
    })

    it('should handle missing nested properties', () => {
      const itemsWithMissing = [
        { id: 1, name: 'Item 1' },
        { id: 2, name: 'Item 2', category: {} },
        { id: 3, name: 'Item 3', category: { name: 'Valid' } },
      ] as any
      const wrapper = mount(DataTable, {
        props: {
          items: itemsWithMissing,
          columns: [{ key: 'category.name', label: 'Category' }],
        },
      })

      const cells = wrapper.findAll('tbody td')
      expect(cells[0].text()).toBe('-')
      expect(cells[1].text()).toBe('-')
      expect(cells[2].text()).toBe('Valid')
    })
  })

  describe('row customization', () => {
    it('should apply row classes from function', () => {
      const rowClass = (item: any, index: number) => {
        if (item.price > 150) return 'table-warning'
        if (index === 0) return 'table-info'
        return ''
      }

      const wrapper = mount(DataTable, {
        props: {
          items: mockItems,
          columns: mockColumns,
          rowClass,
        },
      })

      const rows = wrapper.findAll('tbody tr')
      expect(rows[0].classes()).toContain('table-info')
      expect(rows[1].classes()).toContain('table-warning')
      expect(rows[2].classes()).toContain('table-warning')
    })

    it('should use custom key field', () => {
      const itemsWithCustomKey = [
        { customId: 'a1', name: 'Item A' },
        { customId: 'b2', name: 'Item B' },
      ] as any
      const wrapper = mount(DataTable, {
        props: {
          items: itemsWithCustomKey,
          columns: [{ key: 'name', label: 'Name' }],
          keyField: 'customId',
        },
      })

      const rows = wrapper.findAll('tbody tr')
      expect(rows).toHaveLength(2)
    })

    it('should use index as key when keyField not found', () => {
      const itemsWithoutId = [{ name: 'Item 1' }, { name: 'Item 2' }]

      const wrapper = mount(DataTable, {
        props: {
          items: itemsWithoutId,
          columns: [{ key: 'name', label: 'Name' }],
        },
      })

      expect(wrapper.findAll('tbody tr')).toHaveLength(2)
    })
  })

  describe('slots', () => {
    it('should render actions slot', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: mockItems,
          columns: mockColumns,
        },
        slots: {
          actions: '<button class="btn-edit">Edit</button>',
        },
      })

      expect(wrapper.find('thead th:last-child').text()).toBe('Actions')
      expect(wrapper.findAll('.d-none.d-md-block .btn-edit')).toHaveLength(3)
      expect(wrapper.findAll('.d-block.d-md-none .btn-edit')).toHaveLength(3)
    })

    it('should render custom cell slot', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: mockItems,
          columns: mockColumns,
        },
        slots: {
          'cell-name': '<span class="custom-name">Custom: {{ item.name }}</span>',
        },
      })

      const desktopCustomCells = wrapper.findAll('.d-none.d-md-block .custom-name')
      const mobileCustomCells = wrapper.findAll('.d-block.d-md-none .custom-name')
      expect(desktopCustomCells).toHaveLength(3)
      expect(mobileCustomCells).toHaveLength(3)
      expect(desktopCustomCells[0].text()).toBe('Custom: Item 1')
      expect(mobileCustomCells[0].text()).toBe('Custom: Item 1')
    })

    it('should pass correct props to cell slot', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: mockItems,
          columns: mockColumns,
        },
        slots: {
          'cell-price': `
            <template #cell-price="{ item, value }">
              <span class="price-slot">{{ item.name }}: {{ value }}</span>
            </template>
          `,
        },
      })

      const priceCells = wrapper.findAll('.price-slot')
      expect(priceCells[0].text()).toBe('Item 1: 100')
    })
  })

  describe('responsive behavior', () => {
    it('should add data-label attributes for mobile', () => {
      const wrapper = mount(DataTable, {
        props: {
          items: mockItems.slice(0, 1),
          columns: mockColumns,
        },
      })

      const cells = wrapper.findAll('tbody td')
      expect(cells[0].attributes('data-label')).toBe('ID')
      expect(cells[1].attributes('data-label')).toBe('Name')
      expect(cells[2].attributes('data-label')).toBe('Price')
      expect(cells[3].attributes('data-label')).toBe('Category')
    })
  })
})
