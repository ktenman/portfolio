-- FT ID: 28938271
-- Lightyear UUID: 1ecf2d92-7214-63aa-8df4-d975f6a704cf

INSERT INTO instrument (
    symbol,
    name,
    instrument_category,
    base_currency,
    provider_name,
    current_price,
    created_at,
    updated_at,
    version
) VALUES (
    'CNDX:AEX:EUR',
    'iShares Nasdaq 100',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    1249.80,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
