INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 1.84, 1, '2026-09-25', 'LHV', 0);
