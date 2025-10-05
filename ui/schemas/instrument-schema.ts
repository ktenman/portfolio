import { z } from 'zod'

export const instrumentSchema = z.object({
  symbol: z.string().min(2, 'Symbol must be at least 2 characters'),
  name: z.string().min(1, 'Name is required').max(100, 'Name must be 100 characters or less'),
  providerName: z.string().min(1, 'Data provider is required'),
  category: z.string().min(1, 'Category is required'),
  baseCurrency: z.string().default('EUR'),
  currentPrice: z
    .union([z.string(), z.number()])
    .optional()
    .transform(val => {
      if (val === undefined || val === null || val === '') return undefined
      const num = typeof val === 'string' ? parseFloat(val) : val
      return isNaN(num) ? undefined : num
    })
    .refine(val => val === undefined || val >= 0, {
      message: 'Price must be a positive number',
    }),
})
