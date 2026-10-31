import { ImageResult, isPublicWebUrl } from '../image-utils'

describe('image-utils', () => {
  describe('isPublicWebUrl', () => {
    it('should accept a public URL without credentials', () => {
      expect(isPublicWebUrl('https://example.com/nvidia.png')).toBe(true)
    })

    it.each(['https://logo@example.com/nvidia.png', 'https://:x@example.com/nvidia.png'])(
      'should reject %s because it carries credentials',
      url => {
        expect(isPublicWebUrl(url)).toBe(false)
      }
    )
  })

  describe('type definitions', () => {
    it('should have correct ImageResult shape', () => {
      const result: ImageResult = {
        image: 'http://example.com/image.png',
        thumbnail: 'http://example.com/thumb.png',
        title: 'Test Image',
      }
      expect(result.image).toBe('http://example.com/image.png')
      expect(result.thumbnail).toBe('http://example.com/thumb.png')
      expect(result.title).toBe('Test Image')
    })
  })
})
