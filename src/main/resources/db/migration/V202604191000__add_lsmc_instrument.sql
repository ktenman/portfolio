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
    'LSMC:GER:EUR',
    'Amundi MSCI Semiconductors',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1f117a42-27d7-6c28-875d-f55796e57885',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
