import { createCrudService } from './crud-service-factory'
import { Instrument } from '../models/instrument'
import { CACHE_KEYS } from '../constants/cache-keys'

export const instrumentService = createCrudService<Instrument>(
  '/api/instruments',
  CACHE_KEYS.INSTRUMENTS
)
