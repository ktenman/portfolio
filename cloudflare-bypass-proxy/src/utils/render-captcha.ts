import puppeteer from 'puppeteer'
import * as fs from 'fs'

interface RenderResult {
  base64: string
  buffer: Buffer
}

export async function renderCaptchaImage(
  base64Image: string,
  deviceScaleFactor = 1
): Promise<RenderResult> {
  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  })

  try {
    const page = await browser.newPage()
    await page.setViewport({ width: 800, height: 600, deviceScaleFactor })

    const html = `
      <!DOCTYPE html>
      <html>
      <head>
        <style>
          body { margin: 0; padding: 20px; background: white; }
          img { display: block; }
        </style>
      </head>
      <body>
        <img id="captcha" src="data:image/png;base64,${base64Image}" />
      </body>
      </html>
    `

    await page.setContent(html)
    await page.waitForSelector('#captcha')

    const element = await page.$('#captcha')
    if (!element) {
      throw new Error('Could not find captcha element')
    }

    const screenshot = await element.screenshot({ type: 'png' })
    const buffer = Buffer.from(screenshot)

    return {
      base64: buffer.toString('base64'),
      buffer,
    }
  } finally {
    await browser.close()
  }
}

async function main(): Promise<void> {
  const args = process.argv.slice(2)

  if (args.length < 2) {
    console.log('Usage: npx ts-node src/utils/render-captcha.ts <input.png> <output.png> [scale]')
    process.exit(1)
  }

  const inputPath = args[0]
  const outputPath = args[1]
  const scale = args[2] ? parseFloat(args[2]) : 1

  const imageBuffer = fs.readFileSync(inputPath)
  const base64Image = imageBuffer.toString('base64')

  const result = await renderCaptchaImage(base64Image, scale)
  fs.writeFileSync(outputPath, result.buffer)

  console.log(`Rendered image saved to ${outputPath} (scale: ${scale})`)
  console.log(`BASE64:${result.base64}`)
}

main().catch(err => {
  console.error('Error:', err)
  process.exit(1)
})
