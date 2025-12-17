import * as ort from 'onnxruntime-node'
import * as path from 'path'
import { logger } from './logger'

const CHARACTERS = '2345789ABCDEFHKLMNPRTUVWXYZ'
const IMAGE_HEIGHT = 25
const IMAGE_WIDTH = 65
const MODEL_PATH = path.join(__dirname, '../../model.onnx')

let session: ort.InferenceSession | null = null

async function getSession(): Promise<ort.InferenceSession> {
  if (!session) {
    logger.info('Loading ONNX captcha model...')
    session = await ort.InferenceSession.create(MODEL_PATH, {
      executionProviders: ['cpu'],
    })
    logger.info('ONNX captcha model loaded')
  }
  return session
}

function preprocessImage(base64Image: string): Float32Array {
  const buffer = Buffer.from(base64Image, 'base64')
  const data = new Float32Array(IMAGE_HEIGHT * IMAGE_WIDTH)

  const pngSignature = buffer.slice(0, 8).toString('hex')
  if (pngSignature !== '89504e470d0a1a0a') {
    throw new Error('Invalid PNG format')
  }

  let offset = 8
  let width = 0
  let height = 0
  let bitDepth = 0
  let colorType = 0
  let imageData: Buffer | null = null
  const compressedChunks: Buffer[] = []

  while (offset < buffer.length) {
    const chunkLength = buffer.readUInt32BE(offset)
    const chunkType = buffer.slice(offset + 4, offset + 8).toString('ascii')
    const chunkData = buffer.slice(offset + 8, offset + 8 + chunkLength)

    if (chunkType === 'IHDR') {
      width = chunkData.readUInt32BE(0)
      height = chunkData.readUInt32BE(4)
      bitDepth = chunkData.readUInt8(8)
      colorType = chunkData.readUInt8(9)
    } else if (chunkType === 'IDAT') {
      compressedChunks.push(chunkData)
    } else if (chunkType === 'IEND') {
      break
    }

    offset += 12 + chunkLength
  }

  if (compressedChunks.length > 0) {
    const zlib = require('zlib')
    const compressed = Buffer.concat(compressedChunks)
    imageData = zlib.inflateSync(compressed)
  }

  if (!imageData) {
    throw new Error('Failed to decompress PNG data')
  }

  const bytesPerPixel = colorType === 2 ? 3 : colorType === 6 ? 4 : colorType === 0 ? 1 : 3
  const scanlineLength = 1 + width * bytesPerPixel

  for (let y = 0; y < IMAGE_HEIGHT; y++) {
    const srcY = Math.min(y, height - 1)
    const scanlineStart = srcY * scanlineLength + 1

    for (let x = 0; x < IMAGE_WIDTH; x++) {
      const srcX = Math.min(x, width - 1)
      const pixelStart = scanlineStart + srcX * bytesPerPixel
      let gray: number

      if (colorType === 0) {
        gray = imageData[pixelStart]
      } else if (colorType === 2 || colorType === 6) {
        const r = imageData[pixelStart]
        const g = imageData[pixelStart + 1]
        const b = imageData[pixelStart + 2]
        gray = 0.299 * r + 0.587 * g + 0.114 * b
      } else {
        gray = imageData[pixelStart]
      }

      data[y * IMAGE_WIDTH + x] = gray / 255.0
    }
  }

  return data
}

function argmax(arr: Float32Array | number[]): number {
  let maxIdx = 0
  let maxVal = arr[0]
  for (let i = 1; i < arr.length; i++) {
    if (arr[i] > maxVal) {
      maxVal = arr[i]
      maxIdx = i
    }
  }
  return maxIdx
}

export async function solveCaptchaLocal(base64Image: string): Promise<{
  prediction: string
  confidence: number
}> {
  const startTime = Date.now()

  try {
    const sess = await getSession()
    const imageData = preprocessImage(base64Image)
    const inputTensor = new ort.Tensor('float32', imageData, [1, IMAGE_HEIGHT, IMAGE_WIDTH, 1])
    const inputName = sess.inputNames[0]
    const results = await sess.run({ [inputName]: inputTensor })
    let prediction = ''
    let totalConfidence = 0

    for (let i = 0; i < 4; i++) {
      const outputName = `char_${i}`
      const output = results[outputName]
      if (!output) {
        throw new Error(`Missing output: ${outputName}`)
      }
      const probs = output.data as Float32Array
      const maxIdx = argmax(probs)
      const maxProb = probs[maxIdx]
      prediction += CHARACTERS[maxIdx]
      totalConfidence += maxProb
    }

    const avgConfidence = totalConfidence / 4
    const duration = Date.now() - startTime
    logger.info(`Captcha solved: ${prediction} (confidence: ${(avgConfidence * 100).toFixed(1)}%, ${duration}ms)`)

    return { prediction, confidence: avgConfidence }
  } catch (error) {
    logger.error(`Captcha solving failed: ${error instanceof Error ? error.message : 'Unknown error'}`)
    throw error
  }
}
