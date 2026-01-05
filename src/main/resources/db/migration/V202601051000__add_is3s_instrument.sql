-- Add iShares Edge MSCI World Value Factor instrument
-- Lightyear ID: 1edb2311-2025-62c7-8923-ad904e77bd1a
-- FT Symbol ID: 79062420

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
    'IS3S:GER:EUR',
    'iShares Edge MSCI World Value Factor',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1edb2311-2025-62c7-8923-ad904e77bd1a',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
