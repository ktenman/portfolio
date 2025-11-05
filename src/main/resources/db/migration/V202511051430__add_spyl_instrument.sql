INSERT INTO instrument (symbol, name, instrument_category, base_currency, provider_name)
VALUES ('SPYL:GER:EUR', 'SPDR S&P 500', 'ETF', 'EUR', 'FT') ON CONFLICT (symbol) DO NOTHING;
