INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 17, 33.15, '2025-08-19', 'SWEDBANK');
