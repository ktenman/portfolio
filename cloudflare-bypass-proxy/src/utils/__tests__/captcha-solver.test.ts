import * as fs from 'fs'
import * as path from 'path'

const FIXTURES_DIR = path.join(__dirname, '../../__tests__/fixtures')

describe('Captcha Solver', () => {
  describe('PNG validation', () => {
    it('should detect invalid PNG format', () => {
      const invalidData = Buffer.from('not a valid png')
      const pngSignature = invalidData.slice(0, 8).toString('hex')
      expect(pngSignature).not.toBe('89504e470d0a1a0a')
    })

    it('should detect valid PNG format', () => {
      const imagePath = path.join(FIXTURES_DIR, '2TF8.png')
      const imageBuffer = fs.readFileSync(imagePath)
      const pngSignature = imageBuffer.slice(0, 8).toString('hex')
      expect(pngSignature).toBe('89504e470d0a1a0a')
    })
  })

  describe('Test fixtures', () => {
    const testCases = [
      '2TF8.png',
      '4PUX.png',
      '5AEK.png',
      '5EXE.png',
      '777R.png',
      '79ZL.png',
      'MF4N.png',
      'NTF4.png',
      'PU9T.png',
      'YP33.png',
    ]

    it.each(testCases)('should have test fixture %s', (filename) => {
      const imagePath = path.join(FIXTURES_DIR, filename)
      expect(fs.existsSync(imagePath)).toBe(true)
    })

    it.each(testCases)('should be valid PNG: %s', (filename) => {
      const imagePath = path.join(FIXTURES_DIR, filename)
      const imageBuffer = fs.readFileSync(imagePath)
      const pngSignature = imageBuffer.slice(0, 8).toString('hex')
      expect(pngSignature).toBe('89504e470d0a1a0a')
    })

    it.each(testCases)('should have expected dimensions (65x25): %s', (filename) => {
      const imagePath = path.join(FIXTURES_DIR, filename)
      const imageBuffer = fs.readFileSync(imagePath)
      const width = imageBuffer.readUInt32BE(16)
      const height = imageBuffer.readUInt32BE(20)
      expect(width).toBe(65)
      expect(height).toBe(25)
    })
  })

  describe('Character set', () => {
    const CHARACTERS = '2345789ABCDEFHKLMNPRTUVWXYZ'

    it('should have 27 valid characters', () => {
      expect(CHARACTERS.length).toBe(27)
    })

    it('should not contain ambiguous characters', () => {
      const ambiguous = ['0', '1', '6', 'I', 'O', 'S']
      for (const char of ambiguous) {
        expect(CHARACTERS).not.toContain(char)
      }
    })

    it('should contain expected digits', () => {
      const digits = ['2', '3', '4', '5', '7', '8', '9']
      for (const digit of digits) {
        expect(CHARACTERS).toContain(digit)
      }
    })
  })
})
