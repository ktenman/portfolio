import { useQuery } from '@tanstack/vue-query'
import { ApiError } from '../models/api-error'
import { poffService } from '../services/api'
import { useAuthState } from './use-auth-state'

export const notImported = (error: unknown) => error instanceof ApiError && error.status === 404

export const usePoffCatalogue = () => {
  const { isAuthenticated } = useAuthState()
  return useQuery({
    queryKey: ['poff-catalogue'],
    queryFn: poffService.catalogue,
    enabled: isAuthenticated,
    refetchInterval: 300_000,
    retry: (count, error) => count < 3 && !notImported(error),
    shallow: true,
  })
}
