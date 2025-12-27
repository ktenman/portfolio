-- Add SPDR MSCI World instrument
-- Lightyear ID: 1ef56330-1354-62df-99c7-5dccd8043f1d
-- Lightyear URL: https://lightyear.com/etf/SPPW:XETRA/holdings
-- FT Symbol ID: 519953640

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
    'SPPW:GER:EUR',
    'SPDR MSCI World',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1ef56330-1354-62df-99c7-5dccd8043f1d',
    40.73,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);

-- Portfolio Rebalancing: 2025-12-23 (Lightyear)

-- SELL: Vanguard FTSE All-World
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VWCE:GER:EUR'), 'SELL', 49.455380135, 144.64, '2025-12-23', 'LIGHTYEAR');

-- BUY: SPDR MSCI World
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPPW:GER:EUR'), 'BUY', 175.638397132, 40.73, '2025-12-23', 'LIGHTYEAR');
