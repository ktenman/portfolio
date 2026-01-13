INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 35, 36.00, '2026-01-13', 'LHV', 0);

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 78, 36.00, '2026-01-13', 'SWEDBANK', 3.93);

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 1260, 1.0, '2026-01-13', 'LHV', 0);

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 2804.07, 1.0, '2026-01-13', 'SWEDBANK', 0);
