import { executeCurl } from '../image-utils'

describe('image-utils', () => {
  describe('executeCurl', () => {
    it('should be a function', () => {
      expect(typeof executeCurl).toBe('function')
    })
  })
})
