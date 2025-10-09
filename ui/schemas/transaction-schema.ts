import { z } from 'zod'
import { Platform } from '../models/generated/domain-models'

export const transactionSchema = z.object({
  id: z.number().optional(),
  instrumentId: z.preprocess(val => {
    if (val === '' || val === undefined || val === null) return undefined
    const num = Number(val)
    return isNaN(num) ? val : num
  }, z.number().positive('Please select an instrument')),
  platform: z.nativeEnum(Platform),
  transactionType: z.enum(['BUY', 'SELL'] as const),
  quantity: z.preprocess(
    val => {
      if (val === '' || val === undefined || val === null) return undefined
      const num = Number(val)
      return isNaN(num) || num === 0 ? undefined : num
    },
    z
      .number({ message: 'Quantity is required' })
      .positive('Quantity must be greater than 0')
      .min(0.00000001, 'Quantity is too small')
  ),
  price: z.preprocess(
    val => {
      if (val === '' || val === undefined || val === null) return undefined
      const num = Number(val)
      return isNaN(num) || num === 0 ? undefined : num
    },
    z
      .number({ message: 'Price is required' })
      .positive('Price must be greater than 0')
      .min(0.01, 'Price is too small')
  ),
  commission: z
    .preprocess(val => {
      if (val === '' || val === undefined || val === null) return 0
      const num = Number(val)
      return isNaN(num) ? 0 : num
    }, z.number().min(0, 'Commission cannot be negative').default(0))
    .optional(),
  currency: z.string().default('EUR').optional(),
  transactionDate: z.string().min(1, 'Transaction date is required'),
})
