const CURRENCY_TO_COUNTRY: Record<string, string> = {
  USD: 'us',
  EUR: 'eu',
  GBP: 'gb',
  CHF: 'ch',
  JPY: 'jp',
  CAD: 'ca',
  AUD: 'au',
  SEK: 'se',
  NOK: 'no',
  DKK: 'dk',
  HKD: 'hk',
  SGD: 'sg',
}

export const countryFlagUrl = (code: string): string =>
  `https://hatscripts.github.io/circle-flags/flags/${code.toLowerCase()}.svg`

export function currencyFlagUrl(currency: string | null | undefined): string | null {
  if (!currency) return null
  const code = CURRENCY_TO_COUNTRY[currency.toUpperCase()]
  return code ? countryFlagUrl(code) : null
}
