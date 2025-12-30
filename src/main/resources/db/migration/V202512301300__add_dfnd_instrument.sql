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
    'DFND:PAR:EUR',
    'iShares Global Aerospace & Defence',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1efc26c6-bdb9-61c9-a75b-85fa9e28d0c0',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
