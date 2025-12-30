import { ImageSearchRequest, ImageResult, ImageSearchResponse } from '../image-utils'

describe('image-utils', () => {
  describe('type definitions', () => {
    it('should have correct ImageSearchRequest shape', () => {
      const request: ImageSearchRequest = { query: 'test', maxResults: 5 }
      expect(request.query).toBe('test')
      expect(request.maxResults).toBe(5)
    })

    it('should allow ImageSearchRequest without maxResults', () => {
      const request: ImageSearchRequest = { query: 'test' }
      expect(request.query).toBe('test')
      expect(request.maxResults).toBeUndefined()
    })

    it('should have correct ImageResult shape', () => {
      const result: ImageResult = {
        image: 'http://example.com/image.png',
        thumbnail: 'http://example.com/thumb.png',
        title: 'Test Image',
        width: 100,
        height: 100,
      }
      expect(result.image).toBe('http://example.com/image.png')
      expect(result.thumbnail).toBe('http://example.com/thumb.png')
      expect(result.title).toBe('Test Image')
      expect(result.width).toBe(100)
      expect(result.height).toBe(100)
    })

    it('should have correct ImageSearchResponse shape for success', () => {
      const response: ImageSearchResponse = {
        success: true,
        results: [
          {
            image: 'http://example.com/image.png',
            thumbnail: 'http://example.com/thumb.png',
            title: 'Test',
            width: 200,
            height: 200,
          },
        ],
      }
      expect(response.success).toBe(true)
      expect(response.results).toHaveLength(1)
      expect(response.error).toBeUndefined()
    })

    it('should have correct ImageSearchResponse shape for error', () => {
      const response: ImageSearchResponse = {
        success: false,
        results: [],
        error: 'Something went wrong',
      }
      expect(response.success).toBe(false)
      expect(response.results).toHaveLength(0)
      expect(response.error).toBe('Something went wrong')
    })
  })
})
