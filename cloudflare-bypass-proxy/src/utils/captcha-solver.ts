import * as ort from 'onnxruntime-node'
import * as path from 'path'
import { PNG } from 'pngjs'
import { logger } from './logger'

const CHARACTERS = '2345789ABCDEFHKLMNPRTUVWXYZ'
const HEIGHT = 25
const WIDTH = 65
const MODEL_PATH = path.join(__dirname, '../../model.onnx')

let session: ort.InferenceSession | null = null

async function getSession(): Promise<ort.InferenceSession> {
  if (!session) {
    logger.info('Loading ONNX captcha model...')
    session = await ort.InferenceSession.create(MODEL_PATH, { executionProviders: ['cpu'] })
    logger.info('ONNX captcha model loaded')
  }
  return session
}

function preprocessImage(base64Image: string): Float32Array {
  const png = PNG.sync.read(Buffer.from(base64Image, 'base64'))
  const data = new Float32Array(HEIGHT * WIDTH)

  for (let y = 0; y < HEIGHT; y++) {
    const srcY = Math.min(y, png.height - 1)
    for (let x = 0; x < WIDTH; x++) {
      const srcX = Math.min(x, png.width - 1)
      const idx = (srcY * png.width + srcX) << 2
      const gray = 0.299 * png.data[idx] + 0.587 * png.data[idx + 1] + 0.114 * png.data[idx + 2]
      data[y * WIDTH + x] = gray / 255
    }
  }
  return data
}

export async function solveCaptchaLocal(
  base64Image: string
): Promise<{ prediction: string; confidence: number }> {
  const start = Date.now()

  const sess = await getSession()
  const input = new ort.Tensor('float32', preprocessImage(base64Image), [1, HEIGHT, WIDTH, 1])
  const results = await sess.run({ [sess.inputNames[0]]: input })

  let prediction = ''
  let confidence = 0

  for (let i = 0; i < 4; i++) {
    const probs = results[`char_${i}`].data as Float32Array
    let maxIdx = 0
    for (let j = 1; j < probs.length; j++) if (probs[j] > probs[maxIdx]) maxIdx = j
    prediction += CHARACTERS[maxIdx]
    confidence += probs[maxIdx]
  }

  confidence /= 4
  logger.info(
    `Captcha solved: ${prediction} (${(confidence * 100).toFixed(1)}%, ${Date.now() - start}ms)`
  )

  return { prediction, confidence }
}
