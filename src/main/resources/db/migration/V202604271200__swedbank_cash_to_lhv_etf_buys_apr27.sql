INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 2808.06, 1, '2026-04-27', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 4, 97.15, '2026-04-27', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR'), 'BUY', 3, 321.45, '2026-04-27', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 10, 56.04, '2026-04-27', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 116, 7.699, '2026-04-27', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 1, 7.692, '2026-04-27', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 6.06, 1, '2026-04-27', 'LHV', 0);
