INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY',  250, 25.80, '2025-04-03', 'SWEDBANK');
