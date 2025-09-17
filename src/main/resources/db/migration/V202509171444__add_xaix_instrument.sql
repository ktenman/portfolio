INSERT INTO instrument (symbol, name, instrument_category, base_currency, provider_name)
VALUES ('XAIX:GER:EUR', 'Xtrackers Artificial Intelligence & Big Data', 'ETF', 'EUR', 'FT')
ON CONFLICT (symbol) DO NOTHING;