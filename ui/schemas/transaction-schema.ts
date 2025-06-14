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
        required_error: 'Please select an instrument',
        invalid_type_error: 'Please select an instrument',
      })
      .positive('Please select an instrument')
  ),
  platform: z.nativeEnum(Platform, {
    errorMap: () => ({ message: 'Platform is required' }),
  }),
  transactionType: z.enum(['BUY', 'SELL'], {
    errorMap: () => ({ message: 'Transaction type is required' }),
  }),
  quantity: z.preprocess(
    val => {
      if (val === '' || val === undefined || val === null) return undefined
      const num = Number(val)
      return isNaN(num) || num === 0 ? undefined : num
    },
    z
      .number({
        required_error: 'Quantity is required',
        invalid_type_error: 'Quantity must be a number',
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
        required_error: 'Price is required',
        invalid_type_error: 'Price must be a number',
      })
      .positive('Price must be greater than 0')
      .min(0.01, 'Price is too small')
  ),
  transactionDate: z.string().min(1, 'Transaction date is required'),
})
