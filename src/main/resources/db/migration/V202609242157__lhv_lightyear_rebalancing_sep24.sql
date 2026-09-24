INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 39, 1557.86 / 39, '2026-09-24', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VXUS:GER:EUR'), 'BUY', 361, 1562.77 / 361, '2026-09-24', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 4.91, 1, '2026-09-24', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 59.72, 2384.62 / 59.72, '2026-09-24', 'LIGHTYEAR_BUSINESS', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VXUS:GER:EUR'), 'BUY', 550.274604822, 2384.62 / 550.274604822, '2026-09-24', 'LIGHTYEAR_BUSINESS', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 120.63, 4817.96 / 120.63, '2026-09-24', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VXUS:GER:EUR'), 'BUY', 1111.53741182, 4817.96 / 1111.53741182, '2026-09-24', 'LIGHTYEAR', 0);
