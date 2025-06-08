import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import NavBar from './nav-bar.vue'

// Mock the services and composables
const mockBuildInfo = {
  hash: 'abc123def456789',
  time: '2024-01-15T10:30:00Z',
}

const mockBuildInfoService = {
  getBuildInfo: vi.fn().mockResolvedValue(mockBuildInfo),
}

vi.mock('../services/build-info-service', () => ({
  BuildInfoService: vi.fn(() => mockBuildInfoService),
}))

vi.mock('../composables/use-formatters', () => ({
  useFormatters: () => ({
    formatDate: vi.fn((_date: string, short?: boolean) =>
      short ? '15/01/24 10:30' : '15 Jan 2024 10:30'
    ),
  }),
}))

// Mock APP_CONFIG
vi.mock('../constants/app-config', () => ({
  APP_CONFIG: {
    BUILD_HASH_LENGTH: 7,
  },
}))

// Mock console.error
const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

// Mock router-link component
const RouterLinkMock = {
  template: '<a :href="to" :class="$attrs.class"><slot /></a>',
  props: ['to'],
}

describe('NavBar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('component rendering', () => {
    it('renders navigation links correctly', () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      const navLinks = wrapper.findAll('a')
      expect(navLinks).toHaveLength(4)

      expect(navLinks[0].text()).toBe('Summary')
      expect(navLinks[0].attributes('href')).toBe('/')

      expect(navLinks[1].text()).toBe('Calculator')
      expect(navLinks[1].attributes('href')).toBe('/calculator')

      expect(navLinks[2].text()).toBe('Instruments')
      expect(navLinks[2].attributes('href')).toBe('/instruments')

      expect(navLinks[3].text()).toBe('Transactions')
      expect(navLinks[3].attributes('href')).toBe('/transactions')
    })

    it('renders with proper Bootstrap navbar structure', () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      expect(wrapper.find('.navbar').exists()).toBe(true)
      expect(wrapper.find('.navbar-expand').exists()).toBe(true)
      expect(wrapper.find('.navbar-light').exists()).toBe(true)
      expect(wrapper.find('.bg-light').exists()).toBe(true)
      expect(wrapper.find('.container-fluid').exists()).toBe(true)
      expect(wrapper.find('.navbar-nav').exists()).toBe(true)
    })

    it('renders nav items with correct structure', () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      const navItems = wrapper.findAll('.nav-item')
      expect(navItems).toHaveLength(4)

      navItems.forEach(item => {
        expect(item.find('a').exists()).toBe(true)
        expect(item.find('.nav-indicator').exists()).toBe(true)
      })
    })
  })

  describe('build info functionality', () => {
    it('fetches build info on mount', async () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      // Wait for onMounted to complete
      await wrapper.vm.$nextTick()
      await new Promise(resolve => setTimeout(resolve, 0))

      expect(mockBuildInfoService.getBuildInfo).toHaveBeenCalled()
    })

    it('handles build info fetch failure gracefully', async () => {
      const error = new Error('Network error')
      mockBuildInfoService.getBuildInfo.mockRejectedValueOnce(error)

      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      await wrapper.vm.$nextTick()
      await new Promise(resolve => setTimeout(resolve, 0))

      expect(consoleSpy).toHaveBeenCalledWith('Error fetching build info:', error)
    })

    it('does not display build info when none is available', () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      // Before build info is loaded
      const buildInfoElement = wrapper.find('.build-info')
      expect(buildInfoElement.exists()).toBe(false)
    })
  })

  describe('formatBuildDate function', () => {
    it('formats valid date strings', async () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      await wrapper.vm.$nextTick()

      // Access the component's formatBuildDate method
      const formatBuildDate = (wrapper.vm as any).formatBuildDate

      expect(formatBuildDate('2024-01-15T10:30:00Z')).toBe('15/01/24 10:30')
    })

    it('handles unknown date strings', async () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      const formatBuildDate = (wrapper.vm as any).formatBuildDate

      expect(formatBuildDate('unknown')).toBe('unknown')
      expect(formatBuildDate('')).toBe('unknown')
      expect(formatBuildDate(null)).toBe('unknown')
      expect(formatBuildDate(undefined)).toBe('unknown')
    })

    it('handles invalid date strings', async () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      const formatBuildDate = (wrapper.vm as any).formatBuildDate

      // The mock always returns a formatted date, so test that it returns a string
      const result = formatBuildDate('invalid-date')
      expect(typeof result).toBe('string')
      expect(result.length).toBeGreaterThan(0)
    })
  })

  describe('routes configuration', () => {
    it('has correct routes defined', () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      const routes = (wrapper.vm as any).routes

      expect(routes).toEqual([
        { path: '/', name: 'Summary' },
        { path: '/calculator', name: 'Calculator' },
        { path: '/instruments', name: 'Instruments' },
        { path: '/transactions', name: 'Transactions' },
      ])
    })
  })

  describe('styling and classes', () => {
    it('applies correct CSS classes to navigation elements', () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      const navbar = wrapper.find('.navbar')
      expect(navbar.classes()).toContain('navbar-expand')
      expect(navbar.classes()).toContain('navbar-light')
      expect(navbar.classes()).toContain('bg-light')

      const navLinks = wrapper.findAll('.nav-link')
      expect(navLinks).toHaveLength(4)

      const indicators = wrapper.findAll('.nav-indicator')
      expect(indicators).toHaveLength(4)
    })

    it('includes scroll container for responsive design', () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      expect(wrapper.find('.navbar-scroll-container').exists()).toBe(true)
      expect(wrapper.find('.navbar-content').exists()).toBe(true)
    })
  })

  describe('edge cases', () => {
    it('handles service instantiation', () => {
      expect(() => {
        mount(NavBar, {
          global: {
            components: {
              'router-link': RouterLinkMock,
            },
          },
        })
      }).not.toThrow()
    })

    it('renders without errors when buildInfo is null', () => {
      const wrapper = mount(NavBar, {
        global: {
          components: {
            'router-link': RouterLinkMock,
          },
        },
      })

      // Should render without build info section
      expect(wrapper.find('.build-info').exists()).toBe(false)
      expect(wrapper.find('.navbar').exists()).toBe(true)
    })
  })
})
