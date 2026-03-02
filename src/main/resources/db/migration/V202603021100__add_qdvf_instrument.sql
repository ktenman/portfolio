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
    'QDVF:GER:EUR',
    'iShares S&P 500 Energy Sector',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1edb2311-1f7c-6b55-8923-ad904e77bd1a',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
