-- Add Xtrackers Nasdaq 100 instrument
-- Lightyear ID: 1f098996-8150-6170-8254-e77b526cb347
-- Lightyear URL: https://lightyear.com/en/etf/XNAS:XETRA
-- FT Symbol ID: 640687109

INSERT INTO instrument (
    symbol,
    name,
    instrument_category,
    base_currency,
    provider_name,
    provider_external_id,
    current_price,
    created_at,
    updated_at,
    version
) VALUES (
    'XNAS:GER:EUR',
    'Xtrackers Nasdaq 100',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1f098996-8150-6170-8254-e77b526cb347',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
