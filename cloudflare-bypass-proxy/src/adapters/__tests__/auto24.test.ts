import * as cheerio from 'cheerio'
import { extractVehicleName } from '../auto24'

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
