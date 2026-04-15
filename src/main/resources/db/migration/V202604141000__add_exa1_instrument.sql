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
    'EXA1:AEX:EUR',
    'iShares EURO STOXX Banks 30-15',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1ef5a0f9-9cfa-6753-9ec2-5b729cca6424',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
