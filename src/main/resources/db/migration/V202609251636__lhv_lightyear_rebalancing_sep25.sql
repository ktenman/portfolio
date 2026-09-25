INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'SELL', 3, 39.35 / 3, '2026-09-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'SELL', 356, 4669.30 / 356, '2026-09-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VGLA:GER:EUR'), 'BUY', 1074, 4706.81 / 1074, '2026-09-25', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'SELL', 357.2, 4686.46 / 357.2, '2026-09-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VGLA:GER:EUR'), 'BUY', 1069.358243011, 4686.46 / 1069.358243011, '2026-09-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'SELL', 183.71, 2410.28 / 183.71, '2026-09-25', 'LIGHTYEAR_BUSINESS', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VGLA:GER:EUR'), 'BUY', 549.915525115, 2410.28 / 549.915525115, '2026-09-25', 'LIGHTYEAR_BUSINESS', 0);
