-- FT ID: 544523562
-- Lightyear UUID: 1eda4008-ca17-6926-b60a-654bcfbd8ac3

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
    'VNRA:GER:EUR',
    'Vanguard FTSE North America',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    147.68,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
