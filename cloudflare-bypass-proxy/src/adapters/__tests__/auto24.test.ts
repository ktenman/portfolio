import { Request, Response } from 'express'
import * as cheerio from 'cheerio'
import { auto24Adapter, extractVehicleName } from '../auto24'

const mockExecFile = vi.fn()

vi.mock('child_process', () => ({
  execFile: (...args: unknown[]) => mockExecFile(...args),
}))

vi.mock('util', async () => ({
  ...(await vi.importActual('util')),
  promisify:
    (fn: (...args: unknown[]) => void) =>
    (...args: unknown[]) =>
      new Promise((resolve, reject) => {
        fn(...args, (error: Error | null, result: unknown) => {
          if (error) reject(error)
          else resolve(result)
        })
      }),
}))

describe('Auto24 vehicle name extraction', () => {
  it('should return make and model without the label when price was found', () => {
    const html = `<div class="vehicl_price_request"><form id="vpcNr"><div class="main"><div class="result">
       <div class="row first"><div class="label">Sõiduk:</div>Škoda Superb Combi, 2015</div>
       <div class="row"><div class="label">Keskmine hind:</div><b class="color">7300 € kuni 9100 €</b></div>
     </div></div></form></div>`

    expect(extractVehicleName(cheerio.load(html))).toBe('Škoda Superb Combi, 2015')
  })

  it('should return null when the page has no result block', () => {
    const html =
      '<div class="vehicl_price_request"><div class="errorMessage">Vale kontrollkood.</div></div>'

    expect(extractVehicleName(cheerio.load(html))).toBeNull()
  })
})

describe('Auto24 price route', () => {
  it('should leave the User-Agent to the impersonated browser', async () => {
    vi.spyOn(console, 'log').mockImplementation(() => undefined)
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    mockExecFile.mockImplementation((_cmd, _args, _opts, callback) =>
      callback(null, { stdout: '<html></html>', stderr: '' })
    )
    const res = { status: vi.fn().mockReturnThis(), json: vi.fn() }

    await auto24Adapter.handler(
      { query: { regNumber: '123ÄBC' } } as unknown as Request,
      res as unknown as Response
    )

    expect(mockExecFile.mock.calls.flatMap(call => call[1])).not.toContainEqual(
      expect.stringMatching(/^user-agent:/i)
    )
  })
})
