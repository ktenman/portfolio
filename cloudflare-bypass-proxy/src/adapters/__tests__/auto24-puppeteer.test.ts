import request from 'supertest'
import express from 'express'

const mockClose = jest.fn()
const mockScreenshot = jest.fn()
const mockClick = jest.fn()

const mockPage = {
  setUserAgent: jest.fn(),
  setViewport: jest.fn(),
  goto: jest.fn(),
  waitForSelector: jest.fn(),
  waitForFunction: jest.fn(),
  type: jest.fn(),
  click: jest.fn(),
  $: jest.fn(),
  $eval: jest.fn(),
  cookies: jest.fn(),
  setCookie: jest.fn(),
  content: jest.fn(),
  close: mockClose,
}

const mockBrowser = {
  connected: true,
  newPage: jest.fn().mockResolvedValue(mockPage),
  close: jest.fn(),
}

jest.mock('puppeteer-core', () => ({
  __esModule: true,
  default: {
    launch: jest.fn().mockImplementation(() => Promise.resolve(mockBrowser)),
  },
}))

import { auto24PuppeteerCaptchaAdapter, auto24PuppeteerSubmitAdapter } from '../auto24-puppeteer'

describe('Auto24 Puppeteer Adapter', () => {
  let app: express.Application

  beforeEach(() => {
    app = express()
    app.use(express.json())
    app.post(auto24PuppeteerCaptchaAdapter.path, auto24PuppeteerCaptchaAdapter.handler)
    app.post(auto24PuppeteerSubmitAdapter.path, auto24PuppeteerSubmitAdapter.handler)
    jest.clearAllMocks()
    mockClose.mockResolvedValue(undefined)
  })

  describe('GET CAPTCHA endpoint', () => {
    it('should return 400 if regNr is missing', async () => {
      const response = await request(app).post('/auto24/captcha').send({})

      expect(response.status).toBe(400)
      expect(response.body).toEqual({ error: 'Missing regNr parameter' })
    })

    it('should return CAPTCHA image when found', async () => {
      mockScreenshot.mockResolvedValue(Buffer.from('fake-captcha-image'))

      mockPage.$.mockImplementation((selector: string) => {
        if (selector === '#vpc_captcha') {
          return { screenshot: mockScreenshot, click: mockClick }
        }
        if (selector === '#onetrust-accept-btn-handler') return null
        return null
      })
      mockPage.waitForSelector.mockResolvedValue(true)
      mockPage.waitForFunction.mockResolvedValue(true)
      mockPage.cookies.mockResolvedValue([{ name: 'session', value: 'abc123' }])
      mockPage.content.mockResolvedValue('<html></html>')

      const response = await request(app).post('/auto24/captcha').send({ regNr: '876BCH' })

      expect(response.status).toBe(200)
      expect(response.body).toHaveProperty('sessionId')
      expect(response.body).toHaveProperty('captchaImage')
      expect(response.body.message).toBe('CAPTCHA required')
    })

    it('should return price directly if no CAPTCHA required', async () => {
      mockPage.$.mockImplementation((selector: string) => {
        if (selector === '#onetrust-accept-btn-handler') return null
        return null
      })
      mockPage.waitForSelector.mockImplementation((selector: string) => {
        if (selector === '#vpc_captcha') {
          return Promise.reject(new Error('Timeout'))
        }
        return Promise.resolve(true)
      })
      mockPage.content.mockResolvedValue(`
        <html>
          <div class="result">
            <span class="label">Hind:</span>
            <b>3200 € kuni 8100 €</b>
          </div>
        </html>
      `)

      const response = await request(app).post('/auto24/captcha').send({ regNr: '876BCH' })

      expect(response.status).toBe(200)
      expect(response.body.status).toBe('success')
      expect(response.body.price).toBe('3200 € kuni 8100 €')
    })
  })

  describe('SUBMIT CAPTCHA endpoint', () => {
    it('should return 400 if sessionId is missing', async () => {
      const response = await request(app).post('/auto24/submit').send({ solution: '48h8' })

      expect(response.status).toBe(400)
      expect(response.body).toEqual({ error: 'Missing sessionId or solution parameter' })
    })

    it('should return 400 if solution is missing', async () => {
      const response = await request(app)
        .post('/auto24/submit')
        .send({ sessionId: 'test-session-id' })

      expect(response.status).toBe(400)
      expect(response.body).toEqual({ error: 'Missing sessionId or solution parameter' })
    })

    it('should return 400 for invalid session', async () => {
      const response = await request(app)
        .post('/auto24/submit')
        .send({ sessionId: 'invalid-session', solution: '48h8' })

      expect(response.status).toBe(400)
      expect(response.body).toEqual({ error: 'Invalid or expired session' })
    })
  })

  describe('Adapter configuration', () => {
    it('should have correct captcha adapter configuration', () => {
      expect(auto24PuppeteerCaptchaAdapter.path).toBe('/auto24/captcha')
      expect(auto24PuppeteerCaptchaAdapter.method).toBe('POST')
      expect(auto24PuppeteerCaptchaAdapter.serviceName).toBe('Auto24PuppeteerCaptcha')
      expect(auto24PuppeteerCaptchaAdapter.middleware).toBeDefined()
    })

    it('should have correct submit adapter configuration', () => {
      expect(auto24PuppeteerSubmitAdapter.path).toBe('/auto24/submit')
      expect(auto24PuppeteerSubmitAdapter.method).toBe('POST')
      expect(auto24PuppeteerSubmitAdapter.serviceName).toBe('Auto24PuppeteerSubmit')
      expect(auto24PuppeteerSubmitAdapter.middleware).toBeDefined()
    })
  })
})
