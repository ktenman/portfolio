import { adapters } from '../index'

describe('Adapter Routes', () => {
  it('should have unique routes across all adapters', () => {
    const routes = adapters.map(adapter => `${adapter.method}:${adapter.path}`)
    const uniqueRoutes = new Set(routes)

    expect(routes.length).toBe(uniqueRoutes.size)

    if (routes.length !== uniqueRoutes.size) {
      const duplicates = routes.filter((route, index) => routes.indexOf(route) !== index)
      throw new Error(`Duplicate routes found: ${duplicates.join(', ')}`)
    }
  })

  it('should have valid HTTP methods', () => {
    const validMethods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']

    adapters.forEach(adapter => {
      expect(validMethods).toContain(adapter.method)
    })
  })

  it('should have paths starting with /', () => {
    adapters.forEach(adapter => {
      expect(adapter.path).toMatch(/^\//)
    })
  })

  it('should have service names defined', () => {
    adapters.forEach(adapter => {
      expect(adapter.serviceName).toBeDefined()
      expect(adapter.serviceName).not.toBe('')
    })
  })

  it('should have unique service names', () => {
    const serviceNames = adapters.map(adapter => adapter.serviceName)
    const uniqueServiceNames = new Set(serviceNames)

    expect(serviceNames.length).toBe(uniqueServiceNames.size)
  })

  it('should export correct number of adapters', () => {
    expect(adapters.length).toBeGreaterThan(0)
    expect(adapters.length).toBe(6)
  })
})
