import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ref } from 'vue'
import NavBar from './nav-bar.vue'

interface BuildInfo {
  hash: string
  time: string
}

const mockBuildInfo = ref<BuildInfo | null>(null)

vi.mock('../services/utility-service', () => ({
  utilityService: {
    getBuildInfo: vi.fn(),
  },
}))

vi.mock('@tanstack/vue-query', () => ({
  useQuery: vi.fn(() => ({
    data: mockBuildInfo,
    isLoading: ref(false),
    error: ref(null),
    isError: ref(false),
  })),
}))

describe('NavBar', () => {
  const buildInfoData = {
    hash: 'a1b2c3d4e5f6g7h8',
    time: '2023-12-31T12:00:00Z',
  }

  const routes = [
    { path: '/', name: 'Summary' },
    { path: '/calculator', name: 'Calculator' },
    { path: '/instruments', name: 'Instruments' },
    { path: '/transactions', name: 'Transactions' },
  ]

  const router = createRouter({
    history: createMemoryHistory(),
    routes: routes.map(r => ({ ...r, component: { template: '<div></div>' } })),
  })

  beforeEach(() => {
    vi.clearAllMocks()
    mockBuildInfo.value = null
  })

  const createWrapper = () => {
    return mount(NavBar, {
      global: {
        plugins: [router],
      },
    })
  }

  describe('navigation links', () => {
    it('should render all navigation links', () => {
      const wrapper = createWrapper()

      expect(wrapper.text()).toContain('Summary')
      expect(wrapper.text()).toContain('Calculator')
      expect(wrapper.text()).toContain('Instruments')
      expect(wrapper.text()).toContain('Transactions')
    })
  })

  describe('build info display', () => {
    it('should display build info when available', () => {
      mockBuildInfo.value = buildInfoData
      const wrapper = createWrapper()

      expect(wrapper.text()).toContain('a1b2c3d')
      expect(wrapper.text()).toContain('31.12.2023')
    })

    it('should format date correctly', () => {
      mockBuildInfo.value = {
        hash: 'test123',
        time: '2023-01-15T08:30:00Z',
      }

      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('15.01.2023')
    })

    it('should handle invalid date gracefully', () => {
      mockBuildInfo.value = {
        hash: 'test123',
        time: 'invalid-date',
      }

      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('NaN.NaN.NaN')
    })

    it('should handle unknown date', () => {
      mockBuildInfo.value = {
        hash: 'test123',
        time: 'unknown',
      }

      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('unknown')
    })

    it('should not display build info when null', () => {
      mockBuildInfo.value = null
      const wrapper = createWrapper()
      const buildInfo = wrapper.find('.build-info')
      expect(buildInfo.exists()).toBe(false)
    })

    it('should handle date constructor throwing error', () => {
      const originalDate = global.Date
      const dateSpy = vi.fn().mockImplementation(arg => {
        if (arg === undefined) {
          return originalDate()
        }
        throw new Error('Invalid date')
      })

      Object.assign(dateSpy, {
        now: originalDate.now,
        parse: originalDate.parse,
        UTC: originalDate.UTC,
      })

      global.Date = dateSpy as any

      mockBuildInfo.value = {
        hash: 'test123',
        time: 'throwing-error',
      }

      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('throwing-error')

      global.Date = originalDate
    })

    it('should handle empty date string', () => {
      mockBuildInfo.value = {
        hash: 'test123',
        time: '',
      }

      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('unknown')
    })
  })

})
