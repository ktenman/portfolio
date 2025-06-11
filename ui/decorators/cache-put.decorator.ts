import { cacheService } from '../services/cache-service'

export function CachePut(key: string) {
  return function (_target: object, _propertyName: string, descriptor: PropertyDescriptor) {
    const originalMethod = descriptor.value
    descriptor.value = async function (...args: unknown[]) {
      const result = await originalMethod.apply(this, args)
      if (result) {
        void cacheService.setItem(key, result)
      }
      return result
    }
    return descriptor
  }
}
