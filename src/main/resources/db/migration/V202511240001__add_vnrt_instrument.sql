-- Migration: Add VNRT:AEX:EUR (Vanguard FTSE North America) instrument
-- Date: 2025-11-24
-- FT ID: 79451207
-- Lightyear UUID: 1ecf2da3-e426-68b7-a268-3d0ec5cf88ad

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
    'VNRT:AEX:EUR',
    'Vanguard FTSE North America',
    'ETF',
    'EUR',
    'FT',
    139.22,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
