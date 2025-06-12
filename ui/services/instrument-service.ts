import { Instrument } from '../models/instrument'
import { CACHE_KEYS } from '../constants/cache-keys'
import { BaseCrudService } from './base-crud-service'

export class InstrumentService extends BaseCrudService<Instrument> {
  constructor() {
    super('/api/instruments', CACHE_KEYS.INSTRUMENTS)
  }
}
