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
    'AUCO:AEX:EUR',
    'L&G Gold Mining',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1ed01f90-2987-6a5e-874c-8748b6aff51a',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
