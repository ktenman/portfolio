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
    'SEC0:GER:EUR',
    'iShares MSCI Global Semiconductors',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1f0989b5-4992-62e8-b6b9-fb0c442da4f5',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
