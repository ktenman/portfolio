INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'SELL', 2, 224.25, '2026-03-05', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 448.50, 1, '2026-03-05', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 19, 154, '2026-03-05', 'SWEDBANK', 4.10),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 2921.90, 1, '2026-03-05', 'SWEDBANK', 0);
