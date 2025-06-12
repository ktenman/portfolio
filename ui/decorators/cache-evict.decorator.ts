import { cacheService } from '../services/cache-service'

export function CacheEvict(keys: string | string[]) {
  return function (_target: object, _propertyName: string, descriptor: PropertyDescriptor) {
    const originalMethod = descriptor.value
    descriptor.value = async function (...args: unknown[]) {
      const result = await originalMethod.apply(this, args)

      const keysToEvict = Array.isArray(keys) ? keys : [keys]
      keysToEvict.forEach(key => cacheService.clearItem(key))

      return result
    }
    return descriptor
  }
}
