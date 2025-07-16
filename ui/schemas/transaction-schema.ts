import { z } from 'zod'
import { Platform } from '../models/platform'

export const transactionSchema = z.object({
  id: z.number().optional(),
  instrumentId: z.preprocess(
    val => {
      if (val === '' || val === undefined || val === null) return undefined
      const num = Number(val)
      return isNaN(num) ? val : num
    },
    z
      .number({
        error: 'Please select an instrument',
      })
      .positive('Please select an instrument')
  ),
  platform: z.nativeEnum(Platform, {
    error: 'Platform is required',
  }),
  transactionType: z.enum(['BUY', 'SELL'], {
    error: 'Transaction type is required',
  }),
  quantity: z.preprocess(
    val => {
      if (val === '' || val === undefined || val === null) return undefined
      const num = Number(val)
      return isNaN(num) || num === 0 ? undefined : num
    },
    z
      .number({
        error: 'Quantity is required',
      })
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
      .number({
        error: 'Price is required',
      })
      .positive('Price must be greater than 0')
      .min(0.01, 'Price is too small')
  ),
  transactionDate: z.string().min(1, 'Transaction date is required'),
})
