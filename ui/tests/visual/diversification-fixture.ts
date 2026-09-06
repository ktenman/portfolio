import {
  Currency,
  type DiversificationCalculatorResponseDto,
  type EtfDetailDto,
} from '../../models/generated/domain-models'
import { API_ENDPOINTS } from '../../constants/api'
import { type CachedState } from '../../components/diversification/types'
import { stubInstruments } from './instruments-fixture'
import { apiRoute, type RouteStub } from './stub'

const AVAILABLE_ETFS: EtfDetailDto[] = [
  {
    instrumentId: 101,
    symbol: 'TSTWLD:GER:EUR',
    name: 'Test World Equity Index Tracker',
    allocation: 0,
    ter: 0.6,
    annualReturn: 0.1521,
    currentPrice: 14.62,
    fundCurrency: Currency.EUR,
  },
  {
    instrumentId: 102,
    symbol: 'TSTORS:GER:EUR',
    name: 'Ørsted Green Transition Test Fund',
    allocation: 0,
    ter: 0.35,
    annualReturn: 0.0894,
    currentPrice: 23.8,
    fundCurrency: Currency.EUR,
  },
  {
    instrumentId: 103,
    symbol: 'TSTINF:GER:EUR',
    name: 'Test Global Infrastructure',
    allocation: 0,
    ter: 0.15,
    annualReturn: 0.1146,
    currentPrice: 56.69,
    fundCurrency: Currency.EUR,
  },
  {
    instrumentId: 104,
    symbol: 'TSTDEF:PAR:EUR',
    name: 'Test European Defence',
    allocation: 0,
    ter: 0.55,
    annualReturn: 0.2038,
    currentPrice: 45.86,
    fundCurrency: Currency.EUR,
  },
  {
    instrumentId: 105,
    symbol: 'TSTEMX:GER:EUR',
    name: 'Test Emerging Markets ex-China',
    allocation: 0,
    ter: null,
    annualReturn: 0.0712,
    currentPrice: 20.35,
    fundCurrency: Currency.EUR,
  },
  {
    instrumentId: 106,
    symbol: 'TSTACW:GER:EUR',
    name: 'Test All-Country World',
    allocation: 0,
    ter: 0.07,
    annualReturn: 0.1288,
    currentPrice: 13.29,
    fundCurrency: Currency.EUR,
  },
  {
    instrumentId: 107,
    symbol: 'TSTAER:PAR:EUR',
    name: 'Test Aerospace & Defence',
    allocation: 0,
    ter: 0.35,
    annualReturn: 0.1795,
    currentPrice: 8.59,
    fundCurrency: Currency.EUR,
  },
  {
    instrumentId: 108,
    symbol: 'TSTAIX:GER:EUR',
    name: 'Test Artificial Intelligence',
    allocation: 0,
    ter: 0.35,
    annualReturn: 0.2411,
    currentPrice: 198.1,
    fundCurrency: Currency.EUR,
  },
  {
    instrumentId: 109,
    symbol: 'TSTNDX:NSQ:USD',
    name: 'Test Nasdaq 100',
    allocation: 0,
    ter: 0.2,
    annualReturn: 0.1932,
    currentPrice: 31.74,
    fundCurrency: Currency.USD,
  },
  {
    instrumentId: 110,
    symbol: 'TSTFTS:LSE:GBP',
    name: 'Test FTSE All-Share',
    allocation: 0,
    ter: 0.12,
    annualReturn: 0.0641,
    currentPrice: 12.44,
    fundCurrency: Currency.GBP,
  },
  {
    instrumentId: 112,
    symbol: 'TSTBNK:PAR:EUR',
    name: 'Test European Banks',
    allocation: 0,
    ter: 0.3,
    annualReturn: 0.1503,
    currentPrice: null,
    fundCurrency: Currency.EUR,
  },
  {
    instrumentId: 113,
    symbol: 'TSTSMC:GER:EUR',
    name: 'Test Semiconductor Leaders',
    allocation: 0,
    ter: 0.35,
    annualReturn: 0.2264,
    currentPrice: 36.32,
    fundCurrency: Currency.EUR,
  },
  {
    instrumentId: 115,
    symbol: 'WEBN:GER:EUR',
    name: 'Test World Benchmark',
    allocation: 0,
    ter: 0.07,
    annualReturn: 0.1102,
    currentPrice: 6.41,
    fundCurrency: Currency.EUR,
  },
]

const BENCHMARK_ID = 115

const CONFIG: CachedState = {
  allocations: [
    { instrumentId: 101, value: 32.5 },
    { instrumentId: 103, value: 24 },
    { instrumentId: 108, value: 18.5 },
    { instrumentId: 109, value: 15 },
    { instrumentId: 110, value: 10 },
  ],
  inputMode: 'percentage',
  selectedPlatforms: ['LHV', 'TRADING212'],
  optimizeEnabled: false,
  buyOnlyEnabled: false,
  totalInvestment: 5000,
  actionDisplayMode: 'units',
}

const CALCULATION: DiversificationCalculatorResponseDto = {
  weightedTer: 0.3125,
  weightedAnnualReturn: 0.1687,
  totalUniqueHoldings: 1842,
  holdings: [
    { name: 'NVIDIA', ticker: 'NVDA', percentage: 6.4821, inEtfs: 'TSTWLD, TSTAIX, TSTNDX' },
    { name: 'Microsoft', ticker: 'MSFT', percentage: 5.1043, inEtfs: 'TSTWLD, TSTNDX' },
    { name: 'Apple', ticker: 'AAPL', percentage: 4.7712, inEtfs: 'TSTWLD, TSTNDX' },
    { name: 'ASML Holding N.V.', ticker: 'ASML', percentage: 3.2094, inEtfs: 'TSTAIX, TSTINF' },
    {
      name: 'Taiwan Semiconductor Manufacturing Company Limited',
      ticker: null,
      percentage: 2.8536,
      inEtfs: 'TSTWLD, TSTAIX',
    },
    { name: 'SAP SE', ticker: 'SAP', percentage: 2.1408, inEtfs: 'TSTWLD, TSTINF' },
    { name: 'Siemens AG', ticker: 'SIE', percentage: 1.9265, inEtfs: 'TSTINF' },
    { name: 'Nestlé S.A.', ticker: 'NESN', percentage: 1.5847, inEtfs: 'TSTWLD, TSTFTS' },
    { name: 'Ørsted A/S', ticker: 'ORSTED', percentage: 1.2033, inEtfs: 'TSTINF' },
    { name: 'Novo Nordisk A/S', ticker: 'NOVO-B', percentage: 0.9614, inEtfs: 'TSTWLD' },
  ],
  sectors: [
    { sector: 'Semiconductors', percentage: 21.4062 },
    { sector: 'Software & Cloud Services', percentage: 18.2735 },
    { sector: 'Digital Hardware', percentage: 12.5418 },
    { sector: 'Industrials', percentage: 11.0284 },
    { sector: 'Financials', percentage: 9.7326 },
    { sector: 'Healthcare', percentage: 8.4051 },
    { sector: 'Food & Beverage', percentage: 6.1893 },
    { sector: 'Utilities', percentage: 5.3177 },
    { sector: 'Energy', percentage: 4.2508 },
    { sector: 'Materials', percentage: 2.8546 },
  ],
  industries: [
    { industry: 'Semiconductors & Semiconductor Equipment', percentage: 21.4062 },
    { industry: 'Software', percentage: 12.1835 },
    { industry: 'Banks', percentage: 9.7326 },
    { industry: 'Aerospace & Defense', percentage: 7.4408 },
    { industry: 'Technology Hardware, Storage & Peripherals', percentage: 6.3583 },
    { industry: 'Pharmaceuticals', percentage: 4.2051 },
    { industry: 'Electric Utilities', percentage: 3.3177 },
    { industry: 'Food Products', percentage: 2.1893 },
    { industry: 'Unclassified', percentage: 1.4062 },
  ],
  countries: [
    { countryCode: 'US', countryName: 'United States', percentage: 58.3214 },
    { countryCode: 'DE', countryName: 'Germany', percentage: 9.7052 },
    { countryCode: 'NL', countryName: 'Netherlands', percentage: 6.4831 },
    { countryCode: 'TW', countryName: 'Taiwan', percentage: 5.2096 },
    { countryCode: 'GB', countryName: 'United Kingdom', percentage: 4.8317 },
    { countryCode: 'CH', countryName: 'Switzerland', percentage: 4.1725 },
    { countryCode: 'DK', countryName: 'Denmark', percentage: 3.5648 },
    { countryCode: 'FR', countryName: 'France', percentage: 3.0192 },
    { countryCode: 'JP', countryName: 'Japan', percentage: 2.7415 },
    { countryCode: null, countryName: 'Other', percentage: 1.951 },
  ],
  concentration: {
    top10Percentage: 30.2373,
    largestPosition: null,
  },
}

const BENCHMARK_CALCULATION: DiversificationCalculatorResponseDto = {
  weightedTer: 0.07,
  weightedAnnualReturn: 0.1102,
  totalUniqueHoldings: 3512,
  holdings: [
    { name: 'NVIDIA', ticker: 'NVDA', percentage: 4.2731, inEtfs: 'WEBN' },
    { name: 'Apple', ticker: 'AAPL', percentage: 3.5402, inEtfs: 'WEBN' },
    { name: 'Microsoft', ticker: 'MSFT', percentage: 2.5688, inEtfs: 'WEBN' },
    { name: 'ASML Holding N.V.', ticker: 'ASML', percentage: 0.7104, inEtfs: 'WEBN' },
    { name: 'SAP SE', ticker: 'SAP', percentage: 0.4211, inEtfs: 'WEBN' },
    { name: 'Nestlé S.A.', ticker: 'NESN', percentage: 0.3305, inEtfs: 'WEBN' },
    { name: 'Novo Nordisk A/S', ticker: 'NOVO-B', percentage: 0.2517, inEtfs: 'WEBN' },
  ],
  sectors: [
    { sector: 'Semiconductors', percentage: 9.6021 },
    { sector: 'Software & Cloud Services', percentage: 18.4104 },
    { sector: 'Digital Hardware', percentage: 7.2213 },
    { sector: 'Industrials', percentage: 10.8352 },
    { sector: 'Financials', percentage: 16.9027 },
    { sector: 'Healthcare', percentage: 9.7716 },
    { sector: 'Food & Beverage', percentage: 3.8843 },
    { sector: 'Utilities', percentage: 2.5306 },
    { sector: 'Energy', percentage: 3.9107 },
    { sector: 'Materials', percentage: 3.4082 },
    { sector: 'Consumer Discretionary', percentage: 10.6218 },
    { sector: 'Communication', percentage: 2.9011 },
  ],
  industries: [
    { industry: 'Semiconductors & Semiconductor Equipment', percentage: 9.6021 },
    { industry: 'Software', percentage: 12.3104 },
    { industry: 'Banks', percentage: 8.0035 },
    { industry: 'Aerospace & Defense', percentage: 1.8512 },
    { industry: 'Technology Hardware, Storage & Peripherals', percentage: 4.9013 },
    { industry: 'Pharmaceuticals', percentage: 4.1046 },
    { industry: 'Electric Utilities', percentage: 1.3077 },
    { industry: 'Food Products', percentage: 1.6182 },
    { industry: 'Interactive Media & Services', percentage: 5.4208 },
    { industry: 'Oil, Gas & Consumable Fuels', percentage: 3.2311 },
    { industry: 'Unclassified', percentage: 2.9013 },
  ],
  countries: [
    { countryCode: 'US', countryName: 'United States', percentage: 63.1102 },
    { countryCode: 'JP', countryName: 'Japan', percentage: 5.4207 },
    { countryCode: 'GB', countryName: 'United Kingdom', percentage: 3.5911 },
    { countryCode: 'DE', countryName: 'Germany', percentage: 2.3082 },
    { countryCode: 'NL', countryName: 'Netherlands', percentage: 1.2504 },
    { countryCode: 'TW', countryName: 'Taiwan', percentage: 2.0417 },
    { countryCode: 'CH', countryName: 'Switzerland', percentage: 2.4305 },
    { countryCode: 'DK', countryName: 'Denmark', percentage: 0.7211 },
    { countryCode: 'FR', countryName: 'France', percentage: 2.6801 },
    { countryCode: null, countryName: 'Other', percentage: 16.446 },
  ],
  concentration: {
    top10Percentage: 18.7215,
    largestPosition: null,
  },
}

const isBenchmarkRequest = (body: { allocations: { instrumentId: number }[] }) =>
  body.allocations.length === 1 && body.allocations[0].instrumentId === BENCHMARK_ID

export const stubDiversification: RouteStub = async page => {
  await stubInstruments(page)
  await page.route(apiRoute(`${API_ENDPOINTS.DIVERSIFICATION}/available-etfs`), route =>
    route.fulfill({ json: AVAILABLE_ETFS })
  )
  await page.route(apiRoute(`${API_ENDPOINTS.DIVERSIFICATION}/calculate`), route =>
    route.fulfill({
      json: isBenchmarkRequest(route.request().postDataJSON())
        ? BENCHMARK_CALCULATION
        : CALCULATION,
    })
  )
  await page.route(apiRoute(`${API_ENDPOINTS.DIVERSIFICATION}/config`), route =>
    route.fulfill({ json: CONFIG })
  )
}
