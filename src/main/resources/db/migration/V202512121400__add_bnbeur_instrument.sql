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
    'BNBEUR',
    'BNB/EUR',
    'Cryptocurrency',
    'EUR',
    'BINANCE',
    747.93,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES (
    (SELECT id FROM instrument WHERE symbol = 'BNBEUR'),
    'BUY',
    0.13289525,
    747.9257,
    '2025-12-12',
    'BINANCE'
);
