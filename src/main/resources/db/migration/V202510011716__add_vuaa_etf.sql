INSERT INTO instrument (symbol, name, instrument_category, base_currency, provider_name)
VALUES ('VUAA:GER:EUR', 'Vanguard S&P 500', 'ETF', 'EUR', 'FT') ON CONFLICT (symbol) DO NOTHING;
