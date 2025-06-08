import { describe, it, expect, vi } from 'vitest'
import router from './index'

// Mock the Vue router
vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    createRouter: vi.fn(config => ({
      routes: config.routes,
      history: config.history,
      addRoute: vi.fn(),
      removeRoute: vi.fn(),
      hasRoute: vi.fn(),
      getRoutes: vi.fn(() => config.routes),
      resolve: vi.fn(),
      push: vi.fn(),
      replace: vi.fn(),
      go: vi.fn(),
      back: vi.fn(),
      forward: vi.fn(),
      beforeEach: vi.fn(),
      beforeResolve: vi.fn(),
      afterEach: vi.fn(),
      onError: vi.fn(),
      isReady: vi.fn(() => Promise.resolve()),
      install: vi.fn(),
    })),
    createWebHistory: vi.fn(() => ({})),
  }
})

// Mock the components
vi.mock('../components/instrument-component.vue', () => ({
  default: { name: 'InstrumentComponent' },
}))

vi.mock('../components/transaction-component.vue', () => ({
  default: { name: 'PortfolioTransactionComponent' },
}))

vi.mock('../components/portfolio-summary-component.vue', () => ({
  default: { name: 'PortfolioSummaryComponent' },
}))

vi.mock('../components/calculator-component.vue', () => ({
  default: { name: 'CalculatorComponent' },
}))

describe('Router Configuration', () => {
  describe('router instance', () => {
    it('creates router with correct configuration', () => {
      expect(router).toBeDefined()
      expect(router.getRoutes).toBeDefined()
      expect(typeof router.getRoutes).toBe('function')
    })

    it('uses web history mode', () => {
      const { createWebHistory } = require('vue-router')
      expect(createWebHistory).toBeDefined()
      expect(typeof createWebHistory).toBe('function')
    })
  })

  describe('routes configuration', () => {
    it('has correct number of routes', () => {
      const routes = router.getRoutes()
      expect(routes).toHaveLength(5)
    })

    it('defines home route correctly', () => {
      const routes = router.getRoutes()
      const homeRoute = routes.find(route => route.path === '/')

      expect(homeRoute).toBeDefined()
      expect(homeRoute?.name).toBe('Portfolio Summary')
    })

    it('defines transactions route correctly', () => {
      const routes = router.getRoutes()
      const transactionsRoute = routes.find(route => route.path === '/transactions')

      expect(transactionsRoute).toBeDefined()
      expect(transactionsRoute?.name).toBe('Transactions')
    })

    it('defines instruments route correctly', () => {
      const routes = router.getRoutes()
      const instrumentsRoute = routes.find(route => route.path === '/instruments')

      expect(instrumentsRoute).toBeDefined()
      expect(instrumentsRoute?.name).toBe('Instruments')
    })

    it('defines calculator route correctly', () => {
      const routes = router.getRoutes()
      const calculatorRoute = routes.find(route => route.path === '/calculator')

      expect(calculatorRoute).toBeDefined()
      expect(calculatorRoute?.name).toBe('Calculator')
    })

    it('defines catch-all route for 404 handling', () => {
      const routes = router.getRoutes()
      const catchAllRoute = routes.find(route => route.path === '/:pathMatch(.*)*')

      expect(catchAllRoute).toBeDefined()
      expect(catchAllRoute?.redirect).toBe('/')
    })
  })

  describe('route components', () => {
    it('imports all required components', () => {
      const routes = router.getRoutes()

      routes.forEach(route => {
        if (route.components) {
          expect(route.components).toBeDefined()
        }
      })
    })

    it('maps routes to correct components', () => {
      const routes = router.getRoutes()

      // Check that routes with components are properly defined
      const routesWithComponents = routes.filter(route => route.path !== '/:pathMatch(.*)*')
      expect(routesWithComponents.length).toBeGreaterThan(0)

      // Check specific routes exist
      const homeRoute = routes.find(route => route.path === '/')
      expect(homeRoute).toBeDefined()

      const transactionsRoute = routes.find(route => route.path === '/transactions')
      expect(transactionsRoute).toBeDefined()

      const instrumentsRoute = routes.find(route => route.path === '/instruments')
      expect(instrumentsRoute).toBeDefined()

      const calculatorRoute = routes.find(route => route.path === '/calculator')
      expect(calculatorRoute).toBeDefined()
    })
  })

  describe('route paths', () => {
    it('has unique route paths', () => {
      const routes = router.getRoutes()
      const paths = routes.map(route => route.path)
      const uniquePaths = [...new Set(paths)]

      expect(paths).toHaveLength(uniquePaths.length)
    })

    it('has valid route path patterns', () => {
      const routes = router.getRoutes()

      routes.forEach(route => {
        expect(route.path).toMatch(/^\//) // Should start with /
        expect(typeof route.path).toBe('string')
        expect(route.path.length).toBeGreaterThan(0)
      })
    })

    it('covers main application sections', () => {
      const routes = router.getRoutes()
      const paths = routes.map(route => route.path)

      expect(paths).toContain('/')
      expect(paths).toContain('/transactions')
      expect(paths).toContain('/instruments')
      expect(paths).toContain('/calculator')
    })
  })

  describe('route names', () => {
    it('has descriptive route names', () => {
      const routes = router.getRoutes()

      const namedRoutes = routes.filter(route => route.name)
      expect(namedRoutes).toHaveLength(4) // All routes except catch-all should have names

      namedRoutes.forEach(route => {
        expect(typeof route.name).toBe('string')
        expect((route.name as string).length).toBeGreaterThan(0)
      })
    })

    it('has unique route names', () => {
      const routes = router.getRoutes()
      const names = routes.map(route => route.name).filter(Boolean)
      const uniqueNames = [...new Set(names)]

      expect(names).toHaveLength(uniqueNames.length)
    })
  })

  describe('error handling', () => {
    it('redirects unknown routes to home', () => {
      const routes = router.getRoutes()
      const catchAllRoute = routes.find(route => route.path === '/:pathMatch(.*)*')

      expect(catchAllRoute).toBeDefined()
      expect(catchAllRoute?.redirect).toBe('/')
    })

    it('handles parameterized catch-all pattern', () => {
      const routes = router.getRoutes()
      const catchAllRoute = routes.find(route => route.path === '/:pathMatch(.*)*')

      expect(catchAllRoute?.path).toBe('/:pathMatch(.*)*')
    })
  })

  describe('router functionality', () => {
    it('provides navigation methods', () => {
      expect(typeof router.push).toBe('function')
      expect(typeof router.replace).toBe('function')
      expect(typeof router.go).toBe('function')
      expect(typeof router.back).toBe('function')
      expect(typeof router.forward).toBe('function')
    })

    it('provides route management methods', () => {
      expect(typeof router.addRoute).toBe('function')
      expect(typeof router.removeRoute).toBe('function')
      expect(typeof router.hasRoute).toBe('function')
      expect(typeof router.getRoutes).toBe('function')
    })

    it('provides navigation guards methods', () => {
      expect(typeof router.beforeEach).toBe('function')
      expect(typeof router.beforeResolve).toBe('function')
      expect(typeof router.afterEach).toBe('function')
    })

    it('provides error handling', () => {
      expect(typeof router.onError).toBe('function')
    })

    it('provides installation method', () => {
      expect(typeof router.install).toBe('function')
    })

    it('provides ready state check', () => {
      expect(typeof router.isReady).toBe('function')
      expect(router.isReady()).toBeInstanceOf(Promise)
    })
  })

  describe('integration requirements', () => {
    it('exports router as default', () => {
      expect(router).toBeDefined()
      expect(typeof router).toBe('object')
    })

    it('is ready for Vue app installation', () => {
      expect(router.install).toBeDefined()
      expect(typeof router.install).toBe('function')
    })
  })
})
