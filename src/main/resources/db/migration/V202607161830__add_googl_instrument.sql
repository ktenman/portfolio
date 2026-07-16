INSERT INTO instrument (
    symbol,
    name,
    instrument_category,
    base_currency,
    fund_currency,
    provider_name,
    current_price,
    created_at,
    updated_at,
    version
) VALUES (
    'GOOGL:NSQ:USD',
    'Alphabet Class A',
    'ETF',
    'EUR',
    'USD',
    'LIGHTYEAR',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
