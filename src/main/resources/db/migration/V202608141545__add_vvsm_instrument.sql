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
    'VVSM:GER:EUR',
    'VanEck Semiconductor',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1ef0232c-cd91-6339-8fa7-c7989f8b2ae2',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
