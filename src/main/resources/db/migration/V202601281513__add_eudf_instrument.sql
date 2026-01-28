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
    'EUDF:GER:EUR',
    'WisdomTree Europe Defence',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1efffda3-1025-6a5a-b5c1-0d0aba27fa65',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
