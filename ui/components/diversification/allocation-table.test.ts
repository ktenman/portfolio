import { describe, it, expect } from 'vitest'
import { Currency } from '../../models/generated/domain-models'
import { allocationPair, createEtf, createWrapper } from './allocation-table-fixture'

const actionButton = (wrapper: ReturnType<typeof createWrapper>, title: string) =>
  wrapper.findAll('.action-btn').find(b => b.attributes('title') === title)

describe('AllocationTable', () => {
  it('renders ETF dropdown options with the ticker stripped of exchange suffix', () => {
    const wrapper = createWrapper({
      availableEtfs: [
        createEtf({ symbol: 'AIFS:GER:EUR', name: 'Amundi AI Fund', fundCurrency: Currency.EUR }),
      ],
    })

    const optionTexts = wrapper.findAll('tbody select option').map(o => o.text())
    expect(optionTexts).toContain('AIFS')
    expect(optionTexts).not.toContain('AIFS:GER:EUR')
  })

  describe('rendering', () => {
    it('should render allocation table', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.allocation-table').exists()).toBe(true)
    })

    it('should render header with correct columns in percentage mode', () => {
      const wrapper = createWrapper()
      const headers = wrapper.findAll('th')
      expect(headers[0].text()).toContain('ETF')
      expect(headers[1].text()).toContain('Name')
      expect(headers[2].text()).toContain('Price')
      expect(headers[3].text()).toContain('TER')
      expect(headers[4].text()).toContain('Annual Return')
      expect(headers[5].text()).toContain('Allocation %')
    })
  })

  describe('allocation rows', () => {
    it('should render one row per allocation', () => {
      const wrapper = createWrapper({ allocations: allocationPair })
      const rows = wrapper.findAll('tbody tr')
      expect(rows).toHaveLength(2)
    })

    it.each([
      ['name', 'Vanguard FTSE All-World'],
      ['price', '€120.50'],
      ['TER', '0.22%'],
      ['annual return', '12.00%'],
    ])('should render the %s of the selected ETF', (_column, expected) => {
      const wrapper = createWrapper({ allocations: [{ instrumentId: 1, value: 100 }] })
      expect(wrapper.text()).toContain(expected)
    })
  })

  describe('action buttons', () => {
    it('should render Add ETF button', () => {
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('+ Add ETF')
    })

    it('should emit add event when Add ETF is clicked', async () => {
      const wrapper = createWrapper()
      await actionButton(wrapper, 'Add ETF')?.trigger('click')
      expect(wrapper.emitted('add')).toHaveLength(1)
    })

    it('should emit loadPortfolio event when Load from Portfolio is clicked', async () => {
      const wrapper = createWrapper()
      await actionButton(wrapper, 'Load from Portfolio')?.trigger('click')
      expect(wrapper.emitted('loadPortfolio')).toHaveLength(1)
    })

    it('should disable Load from Portfolio button when loading', () => {
      const wrapper = createWrapper({ isLoadingPortfolio: true })
      expect(actionButton(wrapper, 'Load from Portfolio')?.attributes('disabled')).toBeDefined()
    })

    it('should show spinner when loading portfolio', () => {
      const wrapper = createWrapper({ isLoadingPortfolio: true })
      expect(wrapper.find('.spinner-border').exists()).toBe(true)
    })

    it('should emit clear event when Clear is clicked', async () => {
      const wrapper = createWrapper({ allocations: [{ instrumentId: 1, value: 50 }] })
      await actionButton(wrapper, 'Clear')?.trigger('click')
      expect(wrapper.emitted('clear')).toHaveLength(1)
    })

    it('should disable Clear button when only empty allocation exists', () => {
      const wrapper = createWrapper()
      expect(actionButton(wrapper, 'Clear')?.attributes('disabled')).toBeDefined()
    })
  })

  describe('remove button', () => {
    it('should disable remove button when only one allocation', () => {
      const wrapper = createWrapper()
      const removeBtn = wrapper.find('.remove-btn')
      expect(removeBtn.attributes('disabled')).toBeDefined()
    })

    it('should enable remove button when multiple allocations', () => {
      const wrapper = createWrapper({ allocations: allocationPair })
      const removeBtn = wrapper.find('.remove-btn')
      expect(removeBtn.attributes('disabled')).toBeUndefined()
    })

    it('should emit remove event with index when clicked', async () => {
      const wrapper = createWrapper({ allocations: allocationPair })
      const removeBtns = wrapper.findAll('.remove-btn')
      await removeBtns[1].trigger('click')
      expect(wrapper.emitted('remove')).toEqual([[1]])
    })
  })

  describe('total allocation', () => {
    it('should show total as valid when sum is 100%', () => {
      const wrapper = createWrapper({ allocations: allocationPair })
      expect(wrapper.find('.total-value.valid').exists()).toBe(true)
    })

    it('should show total as invalid when sum is not 100%', () => {
      const wrapper = createWrapper({ allocations: [{ instrumentId: 1, value: 50 }] })
      expect(wrapper.find('.total-value.invalid').exists()).toBe(true)
    })

    it('should show hint when total is invalid in percentage mode', () => {
      const wrapper = createWrapper({ allocations: [{ instrumentId: 1, value: 50 }] })
      expect(wrapper.text()).toContain('(should be 100%)')
    })
  })

  describe('ETF selection filtering', () => {
    it('should filter out already selected ETFs from dropdown', () => {
      const wrapper = createWrapper({
        allocations: [
          { instrumentId: 1, value: 60 },
          { instrumentId: 0, value: 0 },
        ],
      })
      const selects = wrapper.findAll('select')
      const secondSelectOptions = selects[1].findAll('option')
      const optionValues = secondSelectOptions.map(o => o.attributes('value'))
      expect(optionValues).not.toContain('1')
      expect(optionValues).toContain('2')
    })
  })

  describe('export and import buttons', () => {
    it.each(['Export', 'Import'])('should render the %s button', label => {
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain(label)
    })

    it.each([
      ['export', 'Export'],
      ['import', 'Import'],
    ])('should emit the %s event when the %s button is clicked', async (event, label) => {
      const wrapper = createWrapper()
      await actionButton(wrapper, label)?.trigger('click')
      expect(wrapper.emitted(event)).toHaveLength(1)
    })
  })
})
