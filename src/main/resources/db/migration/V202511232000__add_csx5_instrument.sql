INSERT INTO instrument (symbol, name, instrument_category, base_currency, provider_name)
VALUES ('CSX5:AEX:EUR', 'iShares Core EURO STOXX 50', 'ETF', 'EUR', 'FT') ON CONFLICT (symbol) DO NOTHING;
