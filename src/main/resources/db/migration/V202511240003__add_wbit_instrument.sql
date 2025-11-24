-- Migration: Add WBIT:GER:EUR (WisdomTree Physical Bitcoin) instrument
-- Date: 2025-11-24
-- FT ID: 653421505
-- Lightyear UUID: 1ef3aa4c-5f26-6cf0-8eba-bb4404220dad

INSERT INTO instrument (
    symbol,
    name,
    instrument_category,
    base_currency,
    provider_name,
    created_at,
    updated_at,
    version
) VALUES (
    'WBIT:GER:EUR',
    'WisdomTree Physical Bitcoin',
    'ETF',
    'EUR',
    'FT',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
) ON CONFLICT (symbol) DO NOTHING;
