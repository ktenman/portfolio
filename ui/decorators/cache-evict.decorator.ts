import { CacheService } from '../services/cache-service'

export function CacheEvict(key: string) {
  const cacheService = new CacheService()

  return function (_target: object, _propertyName: string, descriptor: PropertyDescriptor) {
    const originalMethod = descriptor.value
    descriptor.value = async function (...args: unknown[]) {
      const result = await originalMethod.apply(this, args)
      cacheService.setItem(key, null)
      return result
    }
    return descriptor
  }
}
