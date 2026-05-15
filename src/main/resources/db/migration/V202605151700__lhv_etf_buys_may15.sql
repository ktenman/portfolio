INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 1, 107.86, '2026-05-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 5, 37.50, '2026-05-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 5, 15.7715, '2026-05-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 13, 8.718, '2026-05-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 0.67, 1, '2026-05-15', 'LHV', 0);
