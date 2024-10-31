-- Sample data insertion
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date)
VALUES ((SELECT id FROM instrument WHERE symbol = 'BTCEUR'),
        'BUY',
        0.00610389,
        65399.8198198198,
        '2024-10-31');
