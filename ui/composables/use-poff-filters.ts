import { computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import type { City } from '../utils/poff'

export const CITIES: City[] = ['Tallinn', 'Tartu']

export const usePoffFilters = () => {
  const route = useRoute()
  const router = useRouter()
  const remembered = useLocalStorage<City>('poff-city', 'Tallinn')
  const set = (key: string, value?: string) =>
    router.replace({ query: { ...route.query, [key]: value } })
  const flag = (key: string) =>
    computed({
      get: () => route.query[key] === '1',
      set: (on: boolean) => set(key, on ? '1' : undefined),
    })
  return {
    day: computed({
      get: () => (typeof route.query.day === 'string' ? route.query.day : null),
      set: (day: string | null) => set('day', day ?? undefined),
    }),
    city: computed({
      get: (): City =>
        CITIES.find(c => c.toLowerCase() === route.query.city) ??
        CITIES.find(c => c === remembered.value) ??
        'Tallinn',
      set: (city: City) => {
        remembered.value = city
        set('city', city.toLowerCase())
      },
    }),
    rated: flag('rated'),
    available: flag('available'),
  }
}
