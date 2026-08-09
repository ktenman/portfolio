import {
  Currency,
  type EtfHoldingBreakdownDto,
  type InstrumentDto,
} from '../../models/generated/domain-models'
import { API_ENDPOINTS } from '../../constants/api'
import { apiRoute, type RouteStub } from './stub'

const ETF_INSTRUMENTS: Pick<InstrumentDto, 'symbol' | 'fundCurrency' | 'currentValue'>[] = [
  { symbol: 'TSTA:GER:EUR', fundCurrency: Currency.EUR, currentValue: 18450.12 },
  { symbol: 'TSTB:LSE:GBP', fundCurrency: Currency.GBP, currentValue: 9320.44 },
  { symbol: 'TSTUS:NSQ:USD', fundCurrency: Currency.USD, currentValue: 7415.9 },
  { symbol: 'GB00TEST0001:LSE:GBP', fundCurrency: Currency.GBP, currentValue: 5240.8 },
  { symbol: 'TSTC:GER:EUR', fundCurrency: Currency.EUR, currentValue: 6120.4 },
  { symbol: 'TSTD:AEX:EUR', fundCurrency: Currency.EUR, currentValue: 4310.75 },
  { symbol: 'TSTE:PAR:EUR', fundCurrency: Currency.EUR, currentValue: 3980.6 },
  { symbol: 'TSTF:MIL:EUR', fundCurrency: Currency.EUR, currentValue: 2875.3 },
  { symbol: 'TSTG:GER:EUR', fundCurrency: Currency.EUR, currentValue: 2410.15 },
  { symbol: 'TSTH:GER:USD', fundCurrency: Currency.USD, currentValue: 3105.7 },
  { symbol: 'TSTI:GER:EUR', fundCurrency: Currency.EUR, currentValue: 1980.05 },
  { symbol: 'TSTJ:LSE:GBP', fundCurrency: Currency.GBP, currentValue: 1450.6 },
  { symbol: 'TSTK:GER:EUR', fundCurrency: Currency.EUR, currentValue: 1620.9 },
  { symbol: 'TSTL:AEX:EUR', fundCurrency: Currency.EUR, currentValue: 1210.45 },
  { symbol: 'TSTM:GER:EUR', fundCurrency: Currency.EUR, currentValue: 980.25 },
]

const HOLDINGS: EtfHoldingBreakdownDto[] = [
  {
    holdingUuid: 'a1000000-0000-4000-8000-000000000001',
    holdingTicker: 'NVDA',
    holdingName: 'NVIDIA',
    percentageOfTotal: 18.421,
    totalValueEur: 12345.67,
    holdingSector: 'Semiconductors',
    holdingCountryCode: 'US',
    holdingCountryName: 'United States',
    inEtfs:
      'TSTA:GER:EUR, TSTB:LSE:GBP, TSTUS:NSQ:USD, GB00TEST0001:LSE:GBP, TSTC:GER:EUR, TSTD:AEX:EUR, TSTE:PAR:EUR, TSTF:MIL:EUR',
    numEtfs: 8,
    platforms: 'LHV, TRADING212',
  },
  {
    holdingUuid: 'a1000000-0000-4000-8000-000000000002',
    holdingTicker: 'ASML',
    holdingName: 'ASML Holding N.V.',
    percentageOfTotal: 12.05,
    totalValueEur: 8076.5,
    holdingSector: 'Semiconductors',
    holdingCountryCode: 'NL',
    holdingCountryName: 'Netherlands',
    inEtfs: 'TSTA:GER:EUR, TSTC:GER:EUR',
    numEtfs: 2,
    platforms: 'LHV',
  },
  {
    holdingUuid: 'a1000000-0000-4000-8000-000000000003',
    holdingTicker: 'NESN',
    holdingName: 'Nestlé S.A.',
    percentageOfTotal: 9.8765,
    totalValueEur: 6618.42,
    holdingSector: 'Food & Beverage',
    holdingCountryCode: 'CH',
    holdingCountryName: 'Switzerland',
    inEtfs: 'TSTB:LSE:GBP, TSTG:GER:EUR',
    numEtfs: 2,
    platforms: 'TRADING212',
  },
  {
    holdingUuid: 'a1000000-0000-4000-8000-000000000004',
    holdingTicker: 'ORSTED',
    holdingName: 'Ørsted A/S',
    percentageOfTotal: 8.3333,
    totalValueEur: 5584.21,
    holdingSector: 'Utilities',
    holdingCountryCode: 'DK',
    holdingCountryName: 'Denmark',
    inEtfs: 'TSTA:GER:EUR, TSTH:GER:USD',
    numEtfs: 2,
    platforms: 'LHV, TRADING212',
  },
  {
    holdingUuid: 'a1000000-0000-4000-8000-000000000005',
    holdingTicker: null,
    holdingName: 'Taiwan Semiconductor Manufacturing Company Limited',
    percentageOfTotal: 7.5,
    totalValueEur: 5025.79,
    holdingSector: 'Semiconductors',
    holdingCountryCode: 'TW',
    holdingCountryName: 'Taiwan',
    inEtfs: 'TSTA:GER:EUR, TSTUS:NSQ:USD',
    numEtfs: 2,
    platforms: 'LHV',
  },
  {
    holdingUuid: null,
    holdingTicker: 'SIE',
    holdingName: 'Siemens AG',
    percentageOfTotal: 6.25,
    totalValueEur: 4188.16,
    holdingSector: 'Industrials',
    holdingCountryCode: 'DE',
    holdingCountryName: 'Germany',
    inEtfs: 'TSTB:LSE:GBP, TSTI:GER:EUR',
    numEtfs: 2,
    platforms: 'TRADING212',
  },
  {
    holdingUuid: 'a1000000-0000-4000-8000-000000000007',
    holdingTicker: 'BTCEUR',
    holdingName: 'Bitcoin',
    percentageOfTotal: 5.1234,
    totalValueEur: 3433.02,
    holdingSector: null,
    holdingCountryCode: null,
    holdingCountryName: null,
    inEtfs: 'TSTA:GER:EUR, TSTJ:LSE:GBP',
    numEtfs: 2,
    platforms: 'LHV',
  },
  {
    holdingUuid: 'a1000000-0000-4000-8000-000000000008',
    holdingTicker: 'SAP',
    holdingName: 'SAP SE',
    percentageOfTotal: 4.0,
    totalValueEur: 2680.42,
    holdingSector: 'Software & Cloud Services',
    holdingCountryCode: 'DE',
    holdingCountryName: 'Germany',
    inEtfs: 'TSTA:GER:EUR, TSTB:LSE:GBP, TSTK:GER:EUR',
    numEtfs: 3,
    platforms: 'LHV, TRADING212',
  },
  {
    holdingUuid: 'a1000000-0000-4000-8000-000000000009',
    holdingTicker: 'MSFT',
    holdingName: 'Microsoft',
    percentageOfTotal: 3.1416,
    totalValueEur: 2105.13,
    holdingSector: 'Software & Cloud Services',
    holdingCountryCode: 'US',
    holdingCountryName: 'United States',
    inEtfs: 'TSTB:LSE:GBP, TSTL:AEX:EUR',
    numEtfs: 2,
    platforms: 'TRADING212',
  },
  {
    holdingUuid: 'a1000000-0000-4000-8000-000000000010',
    holdingTicker: 'AAPL',
    holdingName: 'Apple',
    percentageOfTotal: 0.4,
    totalValueEur: 268.04,
    holdingSector: 'Digital Hardware',
    holdingCountryCode: 'US',
    holdingCountryName: 'United States',
    inEtfs: 'TSTA:GER:EUR, TSTM:GER:EUR',
    numEtfs: 2,
    platforms: 'LHV',
  },
]

const LOGO_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="#0072b2"/></svg>'

const LOGO_BY_UUID =
  /\/api\/logos\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(\?|$)/

export const stubEtfBreakdown: RouteStub = async page => {
  await page.route(apiRoute(API_ENDPOINTS.ETF_BREAKDOWN), route =>
    route.fulfill({ json: HOLDINGS })
  )
  await page.route(apiRoute(API_ENDPOINTS.INSTRUMENTS), route =>
    route.fulfill({ json: { instruments: ETF_INSTRUMENTS, portfolioXirr: null } })
  )
  await page.route(LOGO_BY_UUID, route =>
    route.fulfill({ status: 200, contentType: 'image/svg+xml', body: LOGO_SVG })
  )
  await page.route('**/api/logos/*/candidates', route => route.fulfill({ json: [] }))
  await page.route('**/api/logos/prefetch', route => route.fulfill({ status: 204 }))
}
