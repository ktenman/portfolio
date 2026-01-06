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
    'AIFS:GER:EUR',
    'iShares AI Infrastructure',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1efb76c2-b6a1-6638-914d-2d7971d10ab6',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
