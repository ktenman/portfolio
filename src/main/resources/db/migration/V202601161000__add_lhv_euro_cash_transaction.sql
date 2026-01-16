INSERT INTO portfolio_transaction (
    instrument_id,
    transaction_type,
    quantity,
    price,
    transaction_date,
    platform,
    commission,
    currency,
    realized_profit,
    unrealized_profit,
    remaining_quantity
) VALUES (
    (SELECT id FROM instrument WHERE symbol = 'EUR'),
    'BUY',
    585,
    1.0,
    '2026-01-16',
    'LHV',
    0,
    'EUR',
    0,
    0,
    585
);
