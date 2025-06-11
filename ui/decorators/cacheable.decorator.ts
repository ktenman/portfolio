import { cacheService } from '../services/cache-service'

export function Cacheable(key: string) {

  return function (_target: object, _propertyName: string, descriptor: PropertyDescriptor) {
    const originalMethod = descriptor.value
    descriptor.value = async function (...args: unknown[]) {
      const cachedData = cacheService.getItem(key)
      if (cachedData) return cachedData
      const result = await originalMethod.apply(this, args)
      cacheService.setItem(key, result)
      return result
    }
    return descriptor
  }
}
