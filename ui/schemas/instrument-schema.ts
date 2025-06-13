import { z } from 'zod'

export const instrumentSchema = z.object({
  symbol: z.string().min(1, 'Symbol is required').max(10, 'Symbol must be 10 characters or less'),
  name: z.string().min(1, 'Name is required').max(100, 'Name must be 100 characters or less'),
  providerName: z.string().min(1, 'Data provider is required'),
  category: z.string().min(1, 'Category is required'),
  baseCurrency: z.string().min(1, 'Currency is required'),
})
