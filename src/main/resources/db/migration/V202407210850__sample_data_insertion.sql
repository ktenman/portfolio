-- Sample data insertion
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE.DEX'), 'BUY', 6.866949, 29.125, '2024-07-19');
