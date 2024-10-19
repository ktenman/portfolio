-- Sample data insertion
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date)
VALUES ((SELECT id FROM instrument WHERE symbol = 'BTCEUR'),
        'BUY',
        0.00316683,
        62724.95495,
        '2024-10-19');
