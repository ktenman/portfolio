import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RebalanceStatusCard from './rebalance-status-card.vue'
import { RebalanceStatus } from '../../models/generated/domain-models'

const makeRow = (status: RebalanceStatus, ticker: string) => ({
  status,
  symbol: ticker,
})

describe('RebalanceStatusCard', () => {
  it('renders OK state when all rows are OK', () => {
    const wrapper = mount(RebalanceStatusCard, {
      props: {
        rows: [makeRow(RebalanceStatus.OK, 'VWCE'), makeRow(RebalanceStatus.OK, 'CSPX')],
        currentHoldingsTotal: 10000,
      },
    })

    expect(wrapper.text()).toContain('Portfolio within tolerance')
    expect(wrapper.classes()).toContain('status-ok')
  })

  it('renders DRIFTING state with ticker list when some rows drifting', () => {
    const wrapper = mount(RebalanceStatusCard, {
      props: {
        rows: [makeRow(RebalanceStatus.DRIFTING, 'VWCE'), makeRow(RebalanceStatus.OK, 'CSPX')],
        currentHoldingsTotal: 10000,
      },
    })

    expect(wrapper.text()).toContain('1 holding drifting')
    expect(wrapper.text()).toContain('VWCE')
    expect(wrapper.classes()).toContain('status-drifting')
  })

  it('renders REBALANCE state when any row needs rebalancing', () => {
    const wrapper = mount(RebalanceStatusCard, {
      props: {
        rows: [
          makeRow(RebalanceStatus.REBALANCE, 'VWCE'),
          makeRow(RebalanceStatus.DRIFTING, 'CSPX'),
        ],
        currentHoldingsTotal: 10000,
      },
    })

    expect(wrapper.text()).toContain('1 holding needs rebalancing')
    expect(wrapper.classes()).toContain('status-rebalance')
  })

  it('renders REBALANCE plural form when multiple rows need rebalancing', () => {
    const wrapper = mount(RebalanceStatusCard, {
      props: {
        rows: [
          makeRow(RebalanceStatus.REBALANCE, 'VWCE'),
          makeRow(RebalanceStatus.REBALANCE, 'CSPX'),
        ],
        currentHoldingsTotal: 10000,
      },
    })

    expect(wrapper.text()).toContain('2 holdings need rebalancing')
    expect(wrapper.classes()).toContain('status-rebalance')
  })

  it('does not render when currentHoldingsTotal is zero', () => {
    const wrapper = mount(RebalanceStatusCard, {
      props: {
        rows: [makeRow(RebalanceStatus.OK, 'VWCE')],
        currentHoldingsTotal: 0,
      },
    })

    expect(wrapper.find('.rebalance-status-card').exists()).toBe(false)
  })

  it('emits open-config when configure link is clicked', async () => {
    const wrapper = mount(RebalanceStatusCard, {
      props: {
        rows: [makeRow(RebalanceStatus.OK, 'VWCE')],
        currentHoldingsTotal: 10000,
      },
    })

    await wrapper.find('[data-testid="configure-thresholds"]').trigger('click')
    expect(wrapper.emitted('open-config')).toHaveLength(1)
  })
})
