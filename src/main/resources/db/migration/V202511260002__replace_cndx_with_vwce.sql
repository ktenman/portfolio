DELETE FROM instrument WHERE symbol = 'CNDX:AEX:EUR';

-- FT ID: 544541677
-- Lightyear UUID: 1eda0a07-10b3-63e0-b568-6deedaa217e7

INSERT INTO instrument (
    symbol,
    name,
    instrument_category,
    base_currency,
    provider_name,
    current_price,
    created_at,
    updated_at,
    version
) VALUES (
    'VWCE:GER:EUR',
    'Vanguard FTSE All-World',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    144.39,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
