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
    'IJPA:AEX:EUR',
    'iShares Core MSCI Japan',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1ecf2e07-e681-637b-a268-3d0ec5cf88ad',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
