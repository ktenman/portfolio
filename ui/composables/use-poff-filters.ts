import { computed } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import type { City } from '../utils/poff'
import { SORTS, type FilmSort } from '../utils/poff-films'

export const CITIES: City[] = ['Tallinn', 'Tartu']

const useQueryState = () => {
  const route = useRoute()
  const router = useRouter()
  const set = (key: string, value?: string) =>
    router.replace({ query: { ...route.query, [key]: value } })
  const text = (key: string) =>
    computed({
      get: () => (typeof route.query[key] === 'string' ? route.query[key] : null),
      set: (value: string | null) => set(key, value ?? undefined),
    })
  const flag = (key: string) =>
    computed({
      get: () => route.query[key] === '1',
      set: (on: boolean) => set(key, on ? '1' : undefined),
    })
  return { route, set, text, flag }
}

export const usePoffFilters = () => {
  const { route, set, text, flag } = useQueryState()
  const remembered = useLocalStorage<City>('poff-city', 'Tallinn')
  return {
    day: text('day'),
    film: text('film'),
    query: text('q'),
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

export const usePoffFilmFilters = () => {
  const { route, set, text, flag } = useQueryState()
  return {
    showing: computed({
      get: () => route.query.showing !== '0',
      set: (on: boolean) => set('showing', on ? undefined : '0'),
    }),
    rated: flag('rated'),
    tonight: flag('tonight'),
    genre: text('genre'),
    programme: text('programme'),
    person: text('person'),
    sort: computed({
      get: (): FilmSort => {
        const sort = route.query.sort
        return typeof sort === 'string' && sort in SORTS ? (sort as FilmSort) : 'top'
      },
      set: (sort: FilmSort) => set('sort', sort === 'top' ? undefined : sort),
    }),
  }
}
