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
    'DFEN:GER:EUR',
    'VanEck Defense',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1ef669ad-df78-6d52-af15-0ffcdec674e6',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
