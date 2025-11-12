INSERT INTO instrument (symbol, name, instrument_category, base_currency, provider_name)
VALUES ('WTAI:MIL:EUR', 'WisdomTree Artificial Intelligence', 'ETF', 'EUR', 'FT') ON CONFLICT (symbol) DO NOTHING;
