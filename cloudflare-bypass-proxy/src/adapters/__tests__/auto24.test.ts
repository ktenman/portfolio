import * as cheerio from 'cheerio'
import { extractVehicleName } from '../auto24'

const resultPage = (rows: string) =>
  `<div class="buying_guide_content"><div class="vehicl_price_request"><form id="vpcNr">
     <div class="main"><div class="cont"><div class="to_l"><h1>Vaata sõiduki turuhinda</h1></div>
     <div class="result">${rows}</div></div></div></form></div></div>`

const vehicleRow = (name: string) =>
  `<div class="row first"><div class="label">Sõiduk:</div>${name}</div>`
const priceRow = (price: string) =>
  `<div class="row"><div class="label">Sõiduki keskmine hind:</div><b class="color">${price}</b></div>`

describe('Auto24 vehicle name extraction', () => {
  it('should return make and model without the label when price was found', () => {
    const html = resultPage(vehicleRow('Audi e-tron, 2022') + priceRow('34400 € kuni 38200 €'))

    expect(extractVehicleName(cheerio.load(html))).toBe('Audi e-tron, 2022')
  })

  it('should return non ascii make and model unchanged', () => {
    const html = resultPage(vehicleRow('Škoda Superb Combi, 2015') + priceRow('7300 € kuni 9100 €'))

    expect(extractVehicleName(cheerio.load(html))).toBe('Škoda Superb Combi, 2015')
  })

  it('should return null when the page has no result block', () => {
    const html =
      '<div class="vehicl_price_request"><div class="errorMessage">Vale kontrollkood.</div></div>'

    expect(extractVehicleName(cheerio.load(html))).toBeNull()
  })
})
