-- Update commission fees for portfolio transactions based on actual broker statement

-- Transaction on 15.11.2024 - 10 shares - Commission: 9.90 EUR
UPDATE portfolio_transaction
SET commission = 9.90
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'QDVE:GER:EUR'
    AND pt.transaction_date = '2024-11-15'
    AND pt.quantity = 10
    AND pt.price = 31.465
);

-- Transaction on 06.01.2025 - 69 shares - Commission: 9.90 EUR
UPDATE portfolio_transaction
SET commission = 9.90
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'QDVE:GER:EUR'
    AND pt.transaction_date = '2025-01-06'
    AND pt.quantity = 69
    AND pt.price = 33.24
);

-- Transaction on 27.01.2025 - 92 shares - Commission: 9.90 EUR
UPDATE portfolio_transaction
SET commission = 9.90
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'QDVE:GER:EUR'
    AND pt.transaction_date = '2025-01-27'
    AND pt.quantity = 92
    AND pt.price = 31.47
);

-- Transaction on 28.02.2025 - 104 shares - Commission: 9.90 EUR
UPDATE portfolio_transaction
SET commission = 9.90
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'QDVE:GER:EUR'
    AND pt.transaction_date = '2025-02-28'
    AND pt.quantity = 104
    AND pt.price = 30.78
);

-- Transaction on 04.03.2025 - 117 shares - Commission: 9.90 EUR
UPDATE portfolio_transaction
SET commission = 9.90
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'QDVE:GER:EUR'
    AND pt.transaction_date = '2025-03-04'
    AND pt.quantity = 117
    AND pt.price = 29.72
);

-- Transaction on 10.03.2025 - 151 shares - Commission: 9.90 EUR
UPDATE portfolio_transaction
SET commission = 9.90
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'QDVE:GER:EUR'
    AND pt.transaction_date = '2025-03-10'
    AND pt.quantity = 151
    AND pt.price = 27.9
);

-- Transaction on 03.04.2025 - 250 shares - Commission: 9.90 EUR
UPDATE portfolio_transaction
SET commission = 9.90
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'QDVE:GER:EUR'
    AND pt.transaction_date = '2025-04-03'
    AND pt.quantity = 250
    AND pt.price = 25.8
);

-- Transaction on 16.05.2025 - 40 shares - Commission: 9.90 EUR
UPDATE portfolio_transaction
SET commission = 9.90
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'QDVE:GER:EUR'
    AND pt.transaction_date = '2025-05-16'
    AND pt.quantity = 40
    AND pt.price = 30.008
);

-- Transaction on 16.07.2025 - 38 shares - Commission: 3.90 EUR
UPDATE portfolio_transaction
SET commission = 3.90
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'QDVE:GER:EUR'
    AND pt.transaction_date = '2025-07-16'
    AND pt.quantity = 38
    AND pt.price = 31.78
);

-- Transaction on 19.08.2025 - 17 shares - Commission: 3.90 EUR
UPDATE portfolio_transaction
SET commission = 3.90
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'QDVE:GER:EUR'
    AND pt.transaction_date = '2025-08-19'
    AND pt.quantity = 17
    AND pt.price = 33.15
);

-- Transaction on 18.09.2025 - SELL 452 shares - Commission: 21.52 EUR
UPDATE portfolio_transaction
SET commission = 21.52
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'QDVE:GER:EUR'
    AND pt.transaction_date = '2025-09-18'
    AND pt.quantity = 452
    AND pt.price = 34
    AND pt.transaction_type = 'SELL'
);

-- XAIX transaction on 23.09.2025 - 42 shares - Commission: 8.85 EUR
UPDATE portfolio_transaction
SET commission = 8.85, currency = 'EUR'
WHERE id = (
    SELECT pt.id FROM portfolio_transaction pt
    JOIN instrument i ON pt.instrument_id = i.id
    WHERE i.symbol = 'XAIX:GER:EUR'
    AND pt.transaction_date = '2025-09-23'
    AND pt.quantity = 42
    AND pt.price = 150.50
);