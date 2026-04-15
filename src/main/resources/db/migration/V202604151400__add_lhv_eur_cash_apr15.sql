INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 628.52, 1, '2026-04-15', 'LHV', 0);
