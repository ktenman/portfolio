import puppeteer from 'puppeteer'
import * as fs from 'fs'

interface CaptchaResult {
  base64: string
  buffer: Buffer
  sessionCookies: string
}

export async function captureAuto24Captcha(regNr: string): Promise<CaptchaResult | null> {
  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  })

  try {
    const page = await browser.newPage()

    await page.setUserAgent(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
    )
    await page.setViewport({ width: 1280, height: 800, deviceScaleFactor: 1 })

    console.log('Navigating to Auto24...')
    await page.goto('https://www.auto24.ee/ostuabi/?t=soiduki-turuhinna-paring', {
      waitUntil: 'networkidle2',
      timeout: 30000,
    })

    await new Promise(resolve => setTimeout(resolve, 2000))

    const acceptBtn = await page.$('#onetrust-accept-btn-handler')
    if (acceptBtn) {
      console.log('Closing cookie consent popup...')
      await acceptBtn.click()
      await new Promise(resolve => setTimeout(resolve, 1000))
    }

    const pageContent = await page.content()
    console.log('Page title:', await page.title())

    const hasCloudflare = pageContent.includes('challenge-platform') || pageContent.includes('cf-')
    if (hasCloudflare) {
      console.log('Cloudflare challenge detected, waiting...')
      await new Promise(resolve => setTimeout(resolve, 5000))
    }

    const inputSelector = 'input[name="vpc_reg_nr"]'
    const inputElement = await page.$(inputSelector)
    if (!inputElement) {
      console.log('Input field not found')
      fs.writeFileSync('/tmp/auto24_page.html', pageContent)
      return null
    }

    console.log(`Entering registration number: ${regNr}`)
    await page.type(inputSelector, regNr)
    await page.click('button[name="vpc_reg_search"]')

    console.log('Waiting for CAPTCHA...')
    await page.waitForSelector('#vpc_captcha', { timeout: 10000 })

    console.log('Waiting for CAPTCHA image to load...')
    await page.waitForFunction(
      `(() => {
        const img = document.querySelector('#vpc_captcha');
        return img && img.complete && img.naturalHeight > 0;
      })()`,
      { timeout: 10000 }
    )

    await new Promise(resolve => setTimeout(resolve, 2000))

    await page.screenshot({ path: '/tmp/auto24_full_page.png', fullPage: true })
    console.log('Full page screenshot saved')

    const captchaElement = await page.$('#vpc_captcha')
    if (!captchaElement) {
      console.log('CAPTCHA element not found')
      return null
    }

    const captchaSrc = await page.$eval('#vpc_captcha', el => el.getAttribute('src'))
    console.log(`CAPTCHA src: ${captchaSrc}`)

    console.log('Taking screenshot of CAPTCHA element...')
    const screenshot = await captchaElement.screenshot({ type: 'png' })
    const buffer = Buffer.from(screenshot)

    const cookies = await page.cookies()
    const sessionCookies = cookies.map(c => `${c.name}=${c.value}`).join('; ')

    return {
      base64: buffer.toString('base64'),
      buffer,
      sessionCookies,
    }
  } catch (error) {
    console.error('Error:', error)
    return null
  } finally {
    await browser.close()
  }
}

async function main(): Promise<void> {
  const regNr = process.argv[2] || '463BKH'
  const outputPath = process.argv[3] || '/tmp/auto24_puppeteer_captcha.png'

  console.log(`Capturing CAPTCHA for registration: ${regNr}`)

  const result = await captureAuto24Captcha(regNr)

  if (result) {
    fs.writeFileSync(outputPath, result.buffer)
    console.log(`\nCAPTCHA saved to: ${outputPath}`)
    console.log(`Cookies: ${result.sessionCookies}`)
    console.log(`BASE64:${result.base64}`)
  } else {
    console.log('Failed to capture CAPTCHA')
    process.exit(1)
  }
}

main().catch(err => {
  console.error('Error:', err)
  process.exit(1)
})
