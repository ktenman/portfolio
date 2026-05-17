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
    '84X0:GER:EUR',
    'iShares MSCI EM ex-China',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1f10b439-52a9-6095-9bc1-198365a29746',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
